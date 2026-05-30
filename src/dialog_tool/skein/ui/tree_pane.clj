(ns dialog-tool.skein.ui.tree-pane
  "Right-pane tree graph for the Skein UI.

  Renders the full knot tree as a collapsible, vertically-rooted graph.
  Spine knots (on the selected path from root to active leaf) are shown at
  full opacity; off-spine knots are dimmed. Each node is color-coded by its
  status and shows a truncated command with a tooltip for the full text.
  Lock icons and labels are displayed on the node itself.

  SVG arrows connecting parent to child nodes are drawn dynamically by
  sk.initTreeGraph() / sk.drawTreeArrows() in main.js."
  (:require [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.ui.actions :as actions]
            [dialog-tool.skein.ui.utils :refer [classes]]
            [hyper.core :as h]))

;; ---------------------------------------------------------------------------
;; Helpers

(defn- node-color-class
  "Returns bg+text classes for a node pill by status and spine position."
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
    (str (subs s 0 n) "\u2026")
    (or s "")))

;; ---------------------------------------------------------------------------
;; Node rendering

(defn- render-node
  "Renders a single tree node pill with an optional expand/collapse toggle.
  data-tree-node-id and data-parent-id are read by sk.drawTreeArrows()."
  [*session tree knot-id spine-ids' expanded-ids]
  (let [knot          (tree/get-knot tree knot-id)
        {:keys [id command label locked status children parent-id]} knot
        on-spine?     (contains? spine-ids' id)
        has-kids?     (seq children)
        expanded?     (contains? expanded-ids id)
        active?       (= id (get-in tree [:active-knot-id]))]
    [:div.flex.flex-col.items-center.gap-1
     [:div
      {:class             (classes "flex flex-row items-center gap-1 px-2 py-1 rounded-lg border-2"
                                   "cursor-pointer select-none text-sm min-w-16 max-w-48"
                                   (node-color-class status on-spine? active?)
                                   (if active? "border-primary" "border-transparent"))
       :data-tree-node-id (str id)
       :data-parent-id    (some-> parent-id str)
       :title             command
       :data-on:click     (h/action {:as "tree-node-click"}
                                    (actions/select-tree-node id))}
      (when locked
        [:div.icon.icon-lock.w-3.h-3.shrink-0 {:title "Locked"}])
      (when label
        [:span.text-xs.font-bold.bg-neutral.text-neutral-content.px-1.rounded.shrink-0 label])
      [:span.truncate.font-mono.text-xs (truncate command 22)]]
     (when has-kids?
       [:button
        {:class         "btn btn-xs btn-ghost py-0 px-1 min-h-0 h-5 leading-none"
         :title         (if expanded? "Collapse" "Expand")
         :data-on:click (h/action {:as "toggle-tree-node"}
                                  (actions/toggle-tree-node id))}
        (if expanded? "\u25be" "\u25b8")])]))

;; ---------------------------------------------------------------------------
;; Recursive tree rendering

(defn- render-subtree
  "Recursively renders a node and its (possibly expanded) children."
  [*session tree knot-id spine-ids' expanded-ids]
  (let [children  (tree/find-children tree knot-id)
        sorted    (sort-by (fn [{:keys [id command]}]
                             [(if (contains? spine-ids' id) 0 1)
                              (or command "")])
                           children)
        expanded? (contains? expanded-ids knot-id)]
    [:div.flex.flex-col.items-start.gap-10
     (render-node *session tree knot-id spine-ids' expanded-ids)
     (when (and (seq sorted) expanded?)
       (if (= 1 (count sorted))
         (render-subtree *session tree (:id (first sorted)) spine-ids' expanded-ids)
         [:div.flex.flex-row.items-start.gap-6
          (for [child sorted]
            [:div.flex.flex-col.items-center {:key (:id child)}
             (render-subtree *session tree (:id child) spine-ids' expanded-ids)])]))]))

;; ---------------------------------------------------------------------------
;; Public API

(defn render-tree-pane
  "Renders the full tree pane."
  [*session]
  (let [session        @*session
        tree           (:tree session)
        active-knot-id (get-in tree [:active-knot-id])
        spine-ids'   (into #{} (map :id (tree/selected-knots tree)))
        expanded-ids (or (:expanded-ids session) #{})]
    [:div#tree-pane
     {:class            "overflow-x-auto overflow-y-auto p-4 relative bg-base-200 border-l border-base-300"
      :data-active-knot (str active-knot-id)
      :data-init        "sk.initTreeGraph()"}
     (render-subtree *session tree 0 spine-ids' expanded-ids)]))
