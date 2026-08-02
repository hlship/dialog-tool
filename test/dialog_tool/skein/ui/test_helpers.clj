(ns dialog-tool.skein.ui.test-helpers
  "Test helpers for building session state compatible with hyper.test cursors."
  (:require [dialog-tool.helpers :refer [make-tree response]]
            [dialog-tool.skein.tree :as tree]))

;; ---------------------------------------------------------------------------
;; Session map builder

(defn make-session-map
  "Creates a minimal :session cursor map that skein-page expects.

   The global cursor at [:sessions <id> :data] reads from the :session key,
   so this map is seeded via hyper.test's {:cursors {:session ...}} option."
  [tree]
  {:skein-path         "/test"
   :tree               tree
   :active-knot-id     (get-in tree [:active-knot-id])
   :debug-enabled?     false
   :show-dynamic?      false
   :fixed-width?       false
   :dirty?             false
   :closing?           false
   :loading?           false
   :expanded-ids       #{0}
   :replay-on-launch?  false
   :undo-stack         []
   :redo-stack         []
   :error              nil})

(defn make-minimal-tree
  "A small skein tree: root (START) → look (blessed)."
  []
  (-> (make-tree)
      (tree/add-child 0 1 "look" (response "You are in a rose garden."))
      (tree/bless-response 1)))

(defn make-skein-session-map
  "Build a session map from the minimal tree fixture."
  []
  (make-session-map (make-minimal-tree)))
