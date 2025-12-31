(ns dialog-tool.skein.handlers
  (:require [clj-simple-router.core :as router]
            [dialog-tool.skein.routes :as routes]
            [ring.middleware.content-type :as content-type]
            [huff2.core :as huff]
            [clojure.string :as string]
            [ring.util.response :as response]))

(defn expand-raw-string-body
  "huff returns a wrapper type, huff2.core.RawString, which is not directly compatible
  with http-kit; this middleware converts a RawString :body to a simple String."
  [f]
  (fn [request]
    (let [{:keys [body headers] :as response} (f request)]
      (if (huff/raw-string? body)
        (cond-> (update response :body str)
          (nil? (get headers "Content-Type"))
          (assoc-in [:headers "Content-Type"] "text/html"))
        response))))

(defn wrap-with-response-logger
  [f]
  (fn [request]
    (let [{:keys [uri request-method]} request
          response (f request)]
      (println (or (:status response) "SSE")
               (-> request-method name string/upper-case) uri)
      response)))

(defn wrap-not-found
  [f]
  (fn [request]
    (let [response (f request)]
      (or response
          (response/not-found (str "NOT FOUND: "
                                   (:uri request)))))))

(defn log-errors
  [f]
  (fn [request]
    (try
      (f request)
      (catch Exception e
        (println "Error: " e)
        {:status 500
         :headers {"content-type" "text/plain"}
         :body (str "INTERNAL SERVER ERROR: " (ex-message e))})))

  (def service-handler
    "The main Ring handler for the Skein web service.
  
  Composes a router (from routes/routes) with middleware for:
  - Converting Huff RawString bodies to plain strings
  - Returning 404 for unmatched routes
  - Setting Content-Type headers
  - Logging requests and responses"
    (-> (router/router routes/routes)
        expand-raw-string-body
        wrap-not-found
        content-type/wrap-content-type
        wrap-with-response-logger
        log-errors))
