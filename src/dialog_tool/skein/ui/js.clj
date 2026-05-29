(ns dialog-tool.skein.ui.js
  "JavaScript effects for the client side - mostly, scrolling to knots and managing text field focus."
  (:require [dialog-tool.skein.session :as session]
            [hyper.effects :as effects]))

(defn reset-and-focus-command-input!
  "Clears the command input, scrolls it into view, and focuses it."
  []
  (effects/execute-script! "sk.resetAndFocusCommandInput()"))

(defn navigate-to-knot!
  "Emits side effects, returns session."
  [session knot-id]
  (if-not (-> session (session/get-knot knot-id) :selected-child-id)
    ;; When the active knot has no selected child (it's the leaf knot)
    ;; the scroll-to and focus the command input field.
    (reset-and-focus-command-input!)
    (effects/execute-script! (str "sk.scrollKnotIntoView('" knot-id "')")))
  session)

(defn navigate-to-active-knot!
  "After undo/redo: focuses the command input if the active knot is the leaf,
  otherwise scrolls it into view.
  
  Emits side effects, returns session."
  [session]
  (let [active-id   (session/get-active-knot-id session)
        active-knot (session/get-knot session active-id)]
    (if (nil? active-knot)
      (let [last-knot (-> session
                          session/selected-knots
                          last)]
        (reset-and-focus-command-input!)
        (session/set-active-knot-id session (:id last-knot)))
      (navigate-to-knot! session active-id))))
