(ns dialog-tool.ui.main)

(defn- should-flip-dropdown
  "Given a DOM event, determines if the dropdown should flip above the trigger button.
   Returns true if there's less than 250px of space below the dropdown root element."
  [event]
  (when-let [root (.closest (.-target event) "[data-dropdown-root]")]
    (let [rect        (.getBoundingClientRect root)
          space-below (- js/window.innerHeight (.-bottom rect))]
      (< space-below 250))))

(defn init
  "Called once when the app loads."
  []
  ;; Export to global namespace for use from Datastar expressions
  (set! js/window.shouldFlipDropdown should-flip-dropdown)
  (js/console.log "ClojureScript initialized"))

(defn refresh
  "Called by shadow-cljs after hot-reload."
  []
  (js/console.log "Hot reload complete"))

