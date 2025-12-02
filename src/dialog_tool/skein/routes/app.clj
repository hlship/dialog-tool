(ns dialog-tool.skein.routes.app
  (:require [clj-simple-router.core :as router]
            [huff2.core :refer [html]]))

(def routes
  (router/routes
    "GET /app" []
    {:status 200
     :body   (html [:div#app.relative.px-8 "Fully locked and Loaded!"])}))
