(ns dialog-tool.skein.routes.app
  (:require [clj-simple-router.core :as router]
            [huff2.core :refer [html]]
            [dialog-tool.skein.ui.utils :as ui.utils]
            [dialog-tool.skein.ui.app :as ui.app]))

(defn- app
  [{:keys [*session] :as request}]
  (ui.utils/start-sse request
                      (fn [_]
                        (ui.utils/patch-elements! *session
                                                  (html
                                                    (ui.app/render-app request))))))

(def routes
  (router/routes
    "GET /app" req
    (app req)))
