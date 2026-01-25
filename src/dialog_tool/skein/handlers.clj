(ns dialog-tool.skein.handlers
  (:require [clj-simple-router.core :as router]
            [dialog-tool.skein.routes.app :as app]
            [ring.middleware.content-type :as content-type]
            [huff2.core :as huff]
            [clojure.string :as string]
            [dialog-tool.skein.ui.utils :as utils]
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
         :body (str "INTERNAL SERVER ERROR: " (ex-message e))}))))

(def routes
  (router/routes
   "GET /" []
   (response/redirect "/index.html")

   "POST /action/new-command" req
   (app/new-command req)

   "POST /action/bless/*" req
   (app/bless-knot req)

   "POST /action/bless-to/*" req
   (app/bless-to-knot req)

   "POST /action/replay-to/*" req
   (app/replay-to-knot req)

   "GET /action/select/*" req
   (app/select-knot req)

   "POST /action/new-child/*" req
   (app/prepare-new-child req)

   "GET /action/edit-command/*" req
   (app/open-edit-command req)

   "POST /action/edit-command/*" req
   (app/edit-command req)

   "GET /action/insert-parent/*" req
   (app/open-insert-parent req)

   "POST /action/insert-parent/*" req
   (app/insert-parent req)

   "GET /action/edit-label/*" req
   (app/open-edit-label req)

   "POST /action/edit-label/*" req
   (app/edit-label req)

   "POST /action/dismiss-modal" req
   (app/dismiss-modal req)

   "GET /action/undo" req
   (app/undo req)

   "GET /action/redo" req
   (app/redo req)

   "POST /action/save" req
   (app/save req)

   "POST /action/replay-all" req
   (app/replay-all req)

   "POST /action/delete/*" req
   (app/delete-knot req)

   "POST /action/splice-out/*" req
   (app/splice-out-knot req)

   "GET /action/quit" req
   (app/open-quit req)

   "POST /action/save-and-quit" req
   (app/save-and-quit req)

   "POST /action/quit-without-saving" req
   (app/quit-without-saving req)

   "GET /app" req
   (app/render-app req)

   "GET /**" [path]
   (or
     ;; This is where resources come from in the deployed app
     (response/resource-response path {:root "public"})
     ;; For local development, it's a mix
      ;; Search for local-development compiled files first
    (response/file-response path {:root "out/public"})
      ;; And source files second
    (response/file-response path {:root "public"
                                  :index-files? true}))))

(def service-handler
  "The main Ring handler for the Skein web service.

  Composes a router (from routes/routes) with middleware for:
  - Converting Huff RawString bodies to plain strings
  - Extracting Datastar signals from the incoming request
  - Returning 404 for unmatched routes
  - Setting Content-Type headers
  - Logging requests and responses"
  (-> (router/router routes)
      expand-raw-string-body
      wrap-not-found
      content-type/wrap-content-type
      wrap-with-response-logger
      utils/wrap-parse-signals
      log-errors))
