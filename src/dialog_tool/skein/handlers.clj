(ns dialog-tool.skein.handlers
  (:require [babashka.fs :as fs]
            [clj-simple-router.core :as router]
            [dialog-tool.skein.routes :as routes]
            [ring.middleware.content-type :as content-type]
            [huff2.core :as huff]
            [clojure.core.async :refer [thread]]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as string]
            [cheshire.core :as json]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.session :as session]
            [ring.util.response :as response]))

(def text-plain {"Content-Type" "text/plain"})

(comment
(defn- ->json
  [response]
  (-> response
      (assoc-in [:headers "Content-Type"] "application/json")
      (update :body json/generate-string {:pretty true})))

(defn- tree-delta
  "Computes the main body of the response as a delta for any changed
  (or deleted) knots.  This includes changes to a parent when a new child is added
  (the :children will be different)."
  [old-tree new-tree]
  (let [old-knots (:knots old-tree)
        new-knots (:knots new-tree)
        ;; This will include newly added knots as well as changed
        updates (->> new-knots
                     vals
                     (remove #(= % (get old-knots (:id %)))))
        removed-ids (set/difference
                      (-> old-knots keys set)
                      (-> new-knots keys set))]
    ;; Because we don't care about efficiency, we send the entire updated knot, rather than
    ;; sending just what's changed.
    {:updates     (mapv tree/knot->wire updates)
     :removed_ids removed-ids
     :focus       (:focus new-tree)
     :dirty       (:dirty? new-tree)}))

(defn- bless
  [session payload]
  (session/bless session (:id payload)))

(defn- bless-to
  [session payload]
  (session/bless-to session (:id payload)))

(defn- prep-command
  [s]
  (-> s
      string/trim
      (string/replace #"\s+" " ")))

(defn- new-command
  [session payload]
  (let [{:keys [id command]} payload
        {:keys [active-knot-id]} session
        command' (prep-command command)
        session' (-> (cond-> session
                             (not= id active-knot-id) (session/replay-to! id))
                     (session/command! command'))]
    (assoc session' ::extra-body {:new_id (:active-knot-id session')})))

(defn- edit-command
  [session payload]
  (let [{:keys [id command]} payload]
    (session/edit-command! session id (prep-command command))))

(defn- expose-new-id-in-body
  [session]
  (let [{:keys [new-id]} session]
    (if new-id
      (-> session
          (assoc ::extra-body {:new_id new-id})
          (dissoc :new-id))
      session)))

(defn- insert-parent
  [session payload]
  (let [{:keys [id command]} payload]
    (-> (session/insert-parent! session id (prep-command command))
        expose-new-id-in-body)))

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

(defn- splice-out
  [session {:keys [id]}]
  (->> (session/splice-out! session id)
       expose-new-id-in-body))

(defn- label
  [session {:keys [id label]}]
  (session/label session id (string/trim label)))

(defn- select
  "Selects a knot (ensures it is visible); it may bring in its selected child as well; will be focused."
  [session {:keys [id]}]
  (session/select-knot session id))

(defn- deselect
  [session {:keys [id]}]
  (session/deselect session id))

(defn- start-batch
  "Turns off undo tracking, so the session will start to accumulate changes
  from all the following commands, until end-batch, which renables undo
  tracking and creates a single undo step for the entire batch."
  [session _]
  (-> session
      (session/enable-undo false)
      (assoc ::saved-tree (:tree session)
             ::batching? true)))

(defn- response-body
  [old-tree new-session]
  (-> (tree-delta old-tree (:tree new-session))
      (assoc :enable_undo (-> new-session :undo-stack empty? not)
             :enable_redo (-> new-session :redo-stack empty? not))))

(defn- end-batch
  [session _]
  (let [{::keys [saved-tree]} session]
    (-> session
        (session/enable-undo true)
        session/capture-undo
        ;; This extra-body is merged on top of the fairly empty real body
        (assoc ::extra-body (response-body saved-tree session))
        (dissoc ::saved-tree ::batching?))))

(defn- invoke-handler
  [*session payload handler]
  (let [session @*session
        session' (handler session payload)
        {:keys  [error]
         ::keys [batching? extra-body]} session'
        stripped-session (dissoc session' ::extra-body :error)
        body (cond-> {}
                     (not batching?) (merge (response-body (:tree session) stripped-session))
                     extra-body (merge extra-body)
                     error (assoc :error error))]
    (reset! *session stripped-session)
    {:status 200
     :body   body}))

(def action->handler
  {"bless"         bless
   "bless-to"      bless-to
   "label"         label
   "new-command"   new-command
   "edit-command"  edit-command
   "insert-parent" insert-parent
   "start-batch"   start-batch
   "end-batch"     end-batch
   "save"          save
   "replay"        replay
   "undo"          undo
   "redo"          redo
   "delete"        delete
   "splice-out"    splice-out
   "select"        select
   "deselect"      deselect})

(defn- update-handler
  [request]
  (let [{:keys [body *session *shutdown]} request
        payload (json/parse-string body true)
        {:keys [action]} payload]
    (if (= action "quit")
      (do
        ;; Give everything time enough to return a response
        (thread
          (Thread/sleep 500)
          (@*shutdown))
        {:status 400
         :body   {}})
      (if-let [handler (action->handler action)]
        (invoke-handler *session payload handler)
        {:status 400
         :body   (str "UNKNOWN ACTION: " action)}))))

(defn- api-handler*
  [{:keys [request-method *session] :as request}]
  (cond
    (= :get request-method)
    (let [session @*session]
      (->json {:status 200
               :body   (assoc (response-body {} session)
                         :title (:skein-path session))}))

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

)                                                           ; comment

(defn expand-raw-string-body
  "huff returns a wrapper type, huff2.core.RawString, which is not directly compatible
  with http-kit; this middleware converts a RawString :body to a simple String."
  [f]
  (fn [request]
    (let [response (f request)]
      (if (-> response :body huff/raw-string?)
        (update response :body str)
        response))))

(defn wrap-with-response-logger
  [f]
  (fn [request]
    (let [{:keys [uri request-method]} request
          response (f request)]
      (println (:status response) (-> request-method name string/upper-case) uri)
      response)))

(defn wrap-not-found
  [f]
  (fn [request]
    (let [response (f request)]
      (or response
          (response/not-found (str "NOT FOUND: "
                                   (:uri request)))))))

(def service-handler
  (let [router (router/router routes/routes)]
    (-> router
        expand-raw-string-body
        wrap-not-found
        content-type/wrap-content-type
        wrap-with-response-logger))
  #_ 
  (let [{:keys [uri request-method]} request
        request' (cond-> request
                         (= :post request-method)
                         (update :body slurp))]
    (println (-> request-method name string/upper-case) uri (:body request'))
    (if (= uri "/api")
      (api-handler request')
      (resource-handler uri))))
