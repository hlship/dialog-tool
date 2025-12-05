(ns dialog-tool.ui.main
  (:require [dialog-tool.ui.dropdown :as dropdown]))

(defn init
  "Called once when the app loads."
  []
  (js/console.log "ClojureScript initialized")
  (dropdown/init!))

(defn refresh
  "Called by shadow-cljs after hot-reload."
  []
  (js/console.log "Hot reload complete"))

