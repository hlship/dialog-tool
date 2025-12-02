(ns dialog-tool.skein.routes
  (:require [clj-simple-router.core :as router]
            [ring.util.response :as response]
            [dialog-tool.skein.routes.app :as app]))

(def routes
  (merge
    (router/routes
      "GET /" []
      (response/redirect "/index.html")

      "GET /**" [path]
      (or
        ;; Search for compiled files first
        (response/file-response path {:root "out/public"})
        ;; And source files second
        (response/file-response path {:root         "public"
                                      :index-files? true})))
    app/routes))
