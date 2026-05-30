(ns dialog-tool.skein.ui.tree-pane
  "Right-pane tree graph for the Skein UI.

  Renders the full knot tree as a collapsible, vertically-rooted graph.
  Spine knots (on the selected path from root to active leaf) are shown at
  full opacity; off-spine knots are dimmed. Each node is color-coded by its
  status and shows a truncated command with a tooltip for the full text.
  Lock icons and labels are displayed on the node itself."
  (:require [clojure.string :as string]
            [dialog-tool.env :as env]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.ui.actions :as actions]
            [dialog-tool.skein.ui.common :refer [session-cursor]]
            [dialog-tool.skein.ui.utils :refer [classes]]
            [hyper.core :as h]))

;; ---------------------------------------------------------------------------
;; Helpers

(defn- node-color-class
  "Returns bg+text classes for a node pill.
  Active knot uses primary; spine knots use primary-content; off-spine uses neutral-content."
  [status on-spine? active?]
  (case status
    :new   (cond active?   "bg-warning text-warning-content"
                 on-spine? "bg-warning/80 text-warning-content"
                 :else     "bg-warning/40 text-warning-content")
    :error (cond active?   "bg-error text-error-content"
                 on-spine? "bg-error/80 text-error-content"
                 :else     "bg-error/40 text-error-content")
    (cond active?   "bg-primary text-primary-content"
          on-spine? "bg-primary-content text-primary"
          :else     "bg-neutral-content text-neutral")))

(defn- truncate
  "Truncates s to at most n chars, appending … if longer."
  [s n]
  (if (and s (> (count s) n))
    (str (subs s 0 n) "…")
    (or s "")))

(defn- spine-ids
  "Returns the set of knot IDs on the currently selected spine (root → leaf)."
  [tree]
  (->> (tree/selected-knots tree)
       (map :id)
       set))

;; ---------------------------------------------------------------------------
;; Node rendering

(defn- render-node
  "Renders a single tree node pill with an optional expand/collapse toggle below it."
  [*session tree knot-id spine-ids' expanded-ids]
  (let [knot     (tree/get-knot tree knot-id)
        {:keys [id command label locked status children]} knot
        on-spine? (contains? spine-ids' id)
        has-kids? (seq children)
        expanded? (contains? expanded-ids id)
        active?   (= id (get-in tree [:active-knot-id]))]
    [:div.flex.flex-col.items-start.gap-1

     ;; The node pill
     [:div
      {:class (classes "flex flex-row items-center gap-1 px-2 py-1 rounded-lg border-2"
                       "cursor-pointer select-none text-sm min-w-16 max-w-48"
                       (node-color-class status on-spine? active?)
                       (if active? "border-primary" "border-transparent"))
       :data-tree-node-id (str id)
       :title command
       :data-on:click (h/action {:as "tree-node-click"}
                                (actions/select-tree-node id))}
      (when locked
        [:div.icon.icon-lock.w-3.h-3.shrink-0 {:title "Locked"}])
      (when label
        [:span.text-xs.font-bold.bg-neutral.text-neutral-content.px-1.rounded.shrink-0
         label])
      [:span.truncate.font-mono.text-xs
       (truncate command 22)]]

     ;; Expand/collapse toggle (only when node has children)
     (when has-kids?
       [:button
        {:class "btn btn-xs btn-ghost py-0 px-1 min-h-0 h-5 leading-none"
         :title (if expanded? "Collapse" "Expand")
         :data-on:click (h/action {:as "toggle-tree-node"}
                                  (actions/toggle-tree-node id))}
        (if expanded? "▾" "▸")])]))

;; ---------------------------------------------------------------------------
;; Connector

(defn- connector
  "A thin vertical line connecting a node to its child or children row."
  []
  [:div.w-px.bg-base-300 {:style "min-height: 0.75rem; height: 0.75rem"}])

;; ---------------------------------------------------------------------------
;; Recursive tree rendering
;;
;; All subtree wrappers use items-start so node pills are left-aligned within
;; their column. This prevents the centering-within-a-wide-subtree problem
;; that would push a pill hundreds of pixels off-screen.

(defn- render-subtree
  "Recursively renders a node and its (possibly expanded) children."
  [*session tree knot-id spine-ids' expanded-ids]
  (let [children  (tree/find-children tree knot-id)
        ;; Sort children: spine child first, then by id for stability
        sorted    (sort-by (fn [{:keys [id]}]
                             [(if (contains? spine-ids' id) 0 1) id])
                           children)
        expanded? (contains? expanded-ids knot-id)]
    [:div.flex.flex-col.items-start
     (render-node *session tree knot-id spine-ids' expanded-ids)
     (when (and (seq sorted) expanded?)
       (if (= 1 (count sorted))
         ;; Single child — straight connector then child subtree
         (list
          (connector)
          (render-subtree *session tree (:id (first sorted)) spine-ids' expanded-ids))
         ;; Multiple children — each in its own left-aligned column
         [:div.flex.flex-row.items-start
          (for [child sorted]
            [:div.flex.flex-col.items-start {:key (:id child)}
             (connector)
             (render-subtree *session tree (:id child) spine-ids' expanded-ids)])]))]))

;; ---------------------------------------------------------------------------
;; Public API

(defn render-tree-pane
  "Renders the full tree pane. Intended to be called inside an h/reactive block."
  [*session]
  (let [session     @*session
        tree        (:tree session)
        spine-ids'  (spine-ids tree)
        expanded-ids (or (:expanded-ids session) #{})]
    ;; No background here — bg-base-200 lives on the scrolling wrapper in app.clj
    ;; so it fills the entire pane including the scroll region.
    [:div#tree-pane.p-4
     (render-subtree *session tree 0 spine-ids' expanded-ids)]))
