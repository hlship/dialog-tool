(ns dialog-tool.skein.ui.trace-view
  "Renders a trace tree as interactive hiccup for the trace modal.
   
   The trace state is stored in the session as:
   {:trace {:nodes {id -> node}  ; flat node map (see trace ns)
            :search \"\"         ; current search term
            :node-count n}}      ; total visible node count
   
   Node 0 is an invisible root whose :children are the top-level node IDs."
  (:require [clojure.string :as string]
            [dialog-tool.env :as env]
            [dialog-tool.skein.trace :as trace]
            [dialog-tool.skein.ui.common :refer [modal-cursor]]
            [dialog-tool.skein.ui.utils :refer [classes]]
            [hyper.core :as h]))

(defn- render-source-link
  "Renders a source reference as a clickable link that opens the source viewer
   in a new window, or as plain text if the source can't be parsed.
   node-id is the node's unique numeric :id used to look up the source server-side."
  [source node-id]
  (if (trace/parse-source source)
    [:a.text-xs.text-info.ml-auto.flex-shrink-0.font-mono.hover:underline
     {:href                               (str "/action/source/" node-id)
      :target                             "_blank"
      :title                              source
      :data-on:mouseenter__debounce_400ms (str "sk.showSourcePreview(el,'" node-id "')")
      :data-on:mouseleave                 "sk.hideSourcePreview()"}
     source]
    [:span.text-xs.text-base-content.opacity-50.ml-auto.flex-shrink-0.font-mono
     source]))

(def ^:private type->badge-class
  {:enter "bg-info/20 text-info"
   :query "bg-secondary/20 text-secondary"
   :found "bg-success/20 text-success"
   :now   "bg-warning/20 text-warning"})

(def ^:private type->label
  {:enter "ENTER"
   :query "QUERY"
   :found "FOUND"
   :now   "NOW"})

(defn- render-node
  "Renders a single trace tree node and its visible children.
   nodes is the flat node map, cursor is the session cursor,
   scroll-to-id is the optional :id to scroll into view."
  [*modal nodes node-id scroll-to-id]
  (let [{:keys [id type predicate source children match? expanded]} (get nodes node-id)
        has-children? (seq children)
        scroll-to?    (= id scroll-to-id)]
    [:div.ml-4 {:key id}
     [:div.flex.items-center.gap-1.py-0.5.group.hover:bg-base-200.rounded
      (cond-> {}
        match? (assoc :class "bg-warning opacity-5")
        scroll-to? (assoc :data-init "el.scrollIntoView({block:'center',behavior:'smooth'})"))
      ;; Expand/collapse toggle
      (if has-children?
        [:button.w-5.h-5.flex.items-center.justify-center.text-base-content.opacity-40.hover:opacity-100.rounded.cursor-pointer.flex-shrink-0
         {:type          "button"
          :data-on:click (h/action {:as "trace:toggle-expanded"}
                                   (swap! *modal update :nodes trace/toggle-expanded id))}
         (if expanded "▾" "▸")]
        [:span.w-5.h-5.inline-block.flex-shrink-0])
      ;; Type badge
      [:span {:class (classes "text-xs font-mono px-1.5 py-0.5 rounded flex-shrink-0"
                              (type->badge-class type))}
       (type->label type)]
      ;; Predicate
      [:span.font-mono.text-sm.truncate
       {:class (when match? "font-bold")}
       predicate]
      ;; Source (link to source viewer)
      (render-source-link source id)]
     ;; Children (only if expanded)
     (when (and has-children? expanded)
       [:div
        (for [child-id children]
          (render-node *modal nodes child-id scroll-to-id))])]))

(defn- update-search
  "Applies a search term to the trace state in the cursor."
  [search]
  (swap! (modal-cursor)
         (fn [modal]
           (let [{:keys [nodes]} modal
                 nodes' (if (string/blank? search)
                          (trace/collapse-all nodes)
                          (-> nodes
                              (trace/search-tree search)
                              trace/expand-to-matches))]
             (assoc modal
                    :search search
                    :nodes nodes')))))

(defn- scroll-to-first-match
  []
  (let [*modal (modal-cursor)
        nodes  (get @*modal :nodes)]
    (when-let [match-id (trace/find-first-match nodes)]
      (swap! *modal assoc :scroll-to match-id))))

(defn render-trace-tree
  "Renders the full trace tree view with search input, controls, and results.
   cursor is the session cursor, trace-state is the :trace map from the session."
  [*modal]
  (let [{:keys [nodes search node-count scroll-to]} @*modal
        display-nodes (if (string/blank? search)
                        nodes
                        (trace/search-tree nodes search))
        root          (get display-nodes 0)
        search-signal (h/local-signal :trace-search (or search ""))]
    (list
      ;; Search bar
      [:div.mb-3.flex.items-center.gap-2.flex-shrink-0
       [:input.input.input-bordered.input-sm.flex-1
        {:type        "text"
         :placeholder "Search predicates or sources..."
         :data-bind   (:name search-signal)
         :data-on:keydown__debounce_300ms
         (h/action {:as "trace:search"}
                   (let [search (or $value "")]
                     (env/log-action "trace:search" (pr-str search))
                     (update-search search)
                     (when (= $key "Enter")
                       (scroll-to-first-match))))
         :data-init   "el.focus()"}]]
      ;; Expand/collapse all controls + node count
      [:div.flex.items-center.gap-2.mb-2.flex-shrink-0
       [:button.btn.btn-xs.btn-ghost
        {:type          "button"
         :data-on:click (h/action {:as "trace:expand-all"}
                                  (swap! *modal update :nodes trace/expand-all))}
        "Expand All"]
       [:button.btn.btn-xs.btn-ghost
        {:type          "button"
         :data-on:click (h/action {:as "trace:collapse-all"}
                                  (swap! *modal update :nodes trace/collapse-all))}
        "Collapse All"]
       [:span.text-xs.text-base-content.opacity-50.ml-auto
        (str node-count " nodes")]]
      ;; Tree view (scrollable)
      [:div.flex-1.overflow-y-auto.border.border-base-200.rounded.p-2.bg-base-100
       (for [child-id (:children root)]
         (render-node *modal display-nodes child-id scroll-to))])))
