(ns dialog-tool.ui.main)

(def ^:private menu-height-estimate
  "Estimated height of dropdown menus for flip calculation."
  250)

(defn- should-flip-dropdown
  "Given a trigger button element, determines if the dropdown should flip above it.
   Returns true if there's not enough space below for the menu."
  [trigger-element]
  (let [trigger-rect (.getBoundingClientRect trigger-element)
        space-below  (- js/window.innerHeight (.-bottom trigger-rect))]
    (< space-below menu-height-estimate)))

(defn- toggle-dropdown
  "Toggle the active dropdown. Called from the trigger button's click handler.
   - el: the trigger button element
   - evt: the click event (unused, kept for API consistency)
   - active-dropdown: the current $_activeDropdown signal value
   Returns a map with :activeDropdown and :dropdownFlipped values to set."
  [el _evt active-dropdown]
  (if (= active-dropdown (.-id el))
    #js {:activeDropdown false :dropdownFlipped false}
    #js {:activeDropdown (.-id el) :dropdownFlipped (should-flip-dropdown el)}))

(defn- close-dropdown-outside
  "Handle click outside a dropdown menu. Returns the new activeDropdown value.
   - evt: the click event  
   - id: the dropdown id to check
   - active-dropdown: current $_activeDropdown signal value
   Returns false if should close, or the current value if should stay open."
  [evt id active-dropdown]
  (if (and (= active-dropdown id)
           (not (.closest (.-target evt) (str "#" id))))
    false
    active-dropdown))

(defn init
  "Called once when the app loads."
  []
  ;; Export to global namespace for use from Datastar expressions
  (set! js/window.shouldFlipDropdown should-flip-dropdown)
  (set! js/window.toggleDropdown toggle-dropdown)
  (set! js/window.closeDropdownOutside close-dropdown-outside)
  (js/console.log "ClojureScript initialized"))

(defn refresh
  "Called by shadow-cljs after hot-reload."
  []
  (js/console.log "Hot reload complete"))

