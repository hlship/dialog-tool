(ns dialog-tool.skein.routes
  (:require [clj-simple-router.core :as router]
            [ring.util.response :as response]
            [dialog-tool.skein.routes.app :as app]))

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

   "GET /app" req
   (app/render-app req)

   "GET /**" [path]
   (or
      ;; Search for compiled files first
    (response/file-response path {:root "out/public"})
      ;; And source files second
    (response/file-response path {:root "public"
                                  :index-files? true}))))
