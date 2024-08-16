(ns dialog-tool.skein.handlers
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as string]
            [cheshire.core :as json]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.session :as session])
  (:import (java.io BufferedReader InputStreamReader)))

(def text-plain {"Content-Type" "text/plain"})

(defn- ->json
  [response]
  (-> response
      (assoc-in [:headers "Content-Type"] "application/json")
      (update :body json/generate-string {:pretty true})))

(defn- tree-delta
  "Computes the main body of the response as a delta for any changed
  (or deleted) nodes.  This includes changes to a parent when a new child is added
  (the :children will be different)."
  [old-tree new-tree]
  (let [old-nodes   (:nodes old-tree)
        new-nodes   (:nodes new-tree)
        ;; This will include newly added nodes as well as changed
        updates     (->> new-nodes
                         vals
                         (remove #(= % (get old-nodes (:id %)))))
        removed-ids (set/difference
                      (-> old-nodes keys set)
                      (-> new-nodes keys set))]
    ;; Because we don't care about efficiency, we send the entire updated node, rather than
    ;; sending just what's changed.
    {:updates     (mapv tree/node->wire updates)
     :removed_ids removed-ids}))

(defn- bless
  [session payload]
  (session/bless session (:id payload)))

(defn- new-command
  [session payload]
  (let [{:keys [id command]} payload
        session' (-> session
                     (session/replay-to! id)
                     (session/command! command))
        {:keys [active-node-id]} session']
    (assoc session' ::extra-body {:new_id active-node-id})))

(defn- save
  [session _payload]
  (session/save! session))

(defn- replay
  [session {:keys [id]}]
  (session/replay-to! session id))

(defn- undo
  [session _]
  (session/undo session))

(defn- redo
  [session _]
  (session/redo session))

(defn- delete
  [session {:keys [id]}]
  (session/delete session id))

(defn- response-body
  [old-tree new-session]
  (-> (tree-delta old-tree (:tree new-session))
      (assoc :enable_undo (-> new-session :undo-stack empty? not)
             :enable_redo (-> new-session :redo-stack empty? not))))

(defn- invoke-handler
  [*session payload handler]
  (let [*extra-body (atom nil)
        [session session'] (swap-vals! *session
                                       (fn [session]
                                         (let [session' (handler session payload)]
                                           (reset! *extra-body (::extra-body session'))
                                           (dissoc session' ::extra-body))))
        extra-body  @*extra-body
        body        (-> (response-body (:tree session) session')
                        (merge extra-body))]
    {:status 200
     :body   body}))

(def action->handler
  {"bless"       bless
   "new-command" new-command
   "save"        save
   "replay"      replay
   "undo"        undo
   "redo"        redo
   "delete"      delete})

(defn- update-handler
  [request]
  (let [{:keys [body *session]} request
        payload (with-open [reader (-> body
                                       InputStreamReader.
                                       BufferedReader.)]
                  (json/parse-stream reader true))
        {:keys [action]} payload
        handler (action->handler action)]
    (if handler
      (invoke-handler *session payload handler)
      {:status 400
       :body   (str "UNKNOWN ACTION: " action)})))

(defn- api-handler*
  [{:keys [request-method *session] :as request}]
  (cond
    (= :get request-method)
    (->json {:status 200
             :body   (response-body {} @*session)})

    (= :post request-method)
    (update-handler request)

    ;; This is part of CORS handling, needed when developing the Skein
    ;; against the vite development mode server.
    (= :options request-method)
    {:status 200}

    :else
    {:status  404
     :headers text-plain
     :body    "NOT FOUND: /api"}))

(defn bypass-cors
  [handler]
  (fn [request]
    (update (handler request)
            :headers
            assoc
            "Access-Control-Allow-Origin" "*"
            "Access-Control-Allow-Methods" "*"
            "Access-Control-Allow-Headers" "*"
            ;; Needed?
            "Access-Control-Allow-Credentials" "true")))

(defn json-body
  [handler]
  (fn [request]
    (let [response (handler request)]
      (if (-> response :body map?)
        (->json response)
        response))))

(def api-handler (-> api-handler* json-body bypass-cors))

(defn- extension->content-type
  [uri]
  (let [ext (fs/extension uri)]
    ;; May need to add a few for image types, fonts, etc.
    (get {"css"  "text/css"
          "html" "text/html"
          "js"   "text/javascript"
          "json" "application/json"}
         ext
         "text/plain")))

(defn- resource-handler
  [uri]
  (let [r (io/resource (str "public" uri))]
    (if (some? r)
      {:status  200
       :headers {"Content-Type" (extension->content-type uri)}
       :body    (io/input-stream r)}
      {:status  404
       :headers text-plain
       :body    (str "NOT FOUND: " uri)})))

(defn service-handler
  [request]
  (let [{:keys [uri request-method]} request]
    (println (-> request-method name string/upper-case) uri)
    (if (= uri "/api")
      (api-handler request)
      (resource-handler uri))))
