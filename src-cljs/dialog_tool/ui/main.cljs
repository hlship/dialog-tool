(ns dialog-tool.ui.main)

;; Dropdown functionality is now handled by @tailwindplus/elements
;; (el-dropdown and el-menu web components)

(defn init
  "Called once when the app loads."
  []
  (js/console.log "ClojureScript initialized"))

(defn refresh
  "Called by shadow-cljs after hot-reload."
  []
  (js/console.log "Hot reload complete"))

