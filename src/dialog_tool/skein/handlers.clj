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

(defn- fold-in-children
  [tree]
  (let [{:keys [children]} tree
        f (fn [m id node]
            (assoc m id
                  (assoc node :children (get children id))))]
    (update tree :nodes #(reduce-kv f {} %))))

(defn- tree-delta
  "Computes the main body of the response as a delta for any changed
  (or deleted) nodes."
  [old-tree new-tree]
  ;; Have to fold the children into the nodes to do a proper comparison because
  ;; some operations (such as adding a new node) affects just the children of the
  ;; parent node.
  (let [old-nodes (-> old-tree fold-in-children :nodes)
        new-nodes (-> new-tree fold-in-children :nodes)
        updates (->> new-nodes
                     vals
                     (remove #(= % (get old-nodes (:id %)))))
        removed-ids (set/difference
                      (-> old-nodes keys set)
                      (-> new-nodes keys set))]
    {:updates     (mapv #(tree/node->wire new-tree %) updates)
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

(defn- invoke-handler
  [*session payload handler]
  (let [[session session'] (swap-vals! *session handler payload)
        extra-body (::extra-body session')
        body (cond-> (tree-delta (:tree session)
                                 (:tree session'))
                     extra-body (merge extra-body))]
    {:status  200
     :body    body}))

(def action->handler
  {"bless"       bless
   "new-command" new-command})

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
             :body   (tree/->wire (:tree @*session))})

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