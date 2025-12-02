(ns dialog-tool.skein.routes
  (:require [clj-simple-router.core :as router]
            [ring.util.response :as response]
            [huff2.core :refer [html]]))

(def routes
  (router/routes
    "GET /" []
    (response/redirect "/index.html")

    "GET /**" [path]
    (or
      ;; Search for compiled files first
      (response/file-response path {:root "out/public"})
      ;; And source files second
      (response/file-response path {:root         "public"
                                    :index-files? true}))

    "GET /content/root" []
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (html [:div#message "Fully locked and Loaded!"])}))
