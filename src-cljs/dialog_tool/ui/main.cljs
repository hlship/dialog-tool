(ns dialog-tool.ui.main
  (:require
   ;; Import for side effects - registers web components (el-dropdown, el-menu, etc.)
   ["@tailwindplus/elements"]
   ;; Import for side effects - initializes Datastar data-* attribute handling
   ["/vendor/datastar.js"]))

(defn init
  "Called once when the app loads."
  []
  (js/console.log "ClojureScript initialized"))

(defn refresh
  "Called by shadow-cljs after hot-reload."
  []
  (js/console.log "Hot reload complete"))

