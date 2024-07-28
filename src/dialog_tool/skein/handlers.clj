(ns dialog-tool.skein.handlers
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [cheshire.core :as json]
            [dialog-tool.skein.tree :as tree]))

(def text-plain {"Content-Type" "text/plain"})

(defn- ->json
  [response]
  (-> response
      (assoc-in [:headers "Content-Type"] "application/json")
      (update :body json/generate-string {:pretty true})))

(defn- api-handler
  [{:keys [request-method *session]}]
  (if (= :get request-method)
    (->json {:status 200
             :body   (tree/->wire (:tree @*session))})
    {:status  200
     :headers text-plain
     :body    "NOT YET IMPLEMENTED"}))

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
  (let [{:keys [uri request-method content-type body]} request]
    (println (-> request-method name string/upper-case) uri)
    (if (= uri "/api")
      (api-handler request)
      (resource-handler uri))))