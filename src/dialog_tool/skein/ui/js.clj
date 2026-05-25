(ns dialog-tool.skein.ui.js
  (:require [hyper.effects :as effects]))

(defn scroll-knot-into-view!
  "Emits a JS effect that smoothly scrolls the given knot into view."
  [knot-id]
  (effects/execute-script! (str "sk.scrollKnotIntoView('" knot-id "')")))

(defn reset-and-focus-command-input!
  "Clears the command input, scrolls it into view, and focuses it."
  []
  (effects/execute-script! "sk.resetAndFocusCommandInput()"))

(defn focus-if-leaf!
  "Focuses the command input if knot-id is the leaf of the selected path
  (i.e. has no selected child).  Returns the session cursor."
  [*session knot-id]
  (when (nil? (get-in @*session [:tree :selected knot-id]))
    (reset-and-focus-command-input!))
  *session)

(defn navigate-to-active-knot!
  "After undo/redo: focuses the command input if the active knot is the leaf,
  otherwise scrolls it into view."
  [*session]
  (let [active-id (get-in @*session [:tree :active-knot-id])]
    (if (nil? (get-in @*session [:tree :selected active-id]))
      (reset-and-focus-command-input!)
      (scroll-knot-into-view! active-id))))
