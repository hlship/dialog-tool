(ns dialog-tool.skein.ui.trace-view
  "Renders a trace tree as interactive hiccup for the trace modal.
   
   The trace state is stored in the session as:
   {:trace {:tree [...]          ; parsed trace tree (from trace/build-tree)
            :expanded #{...}     ; set of path strings for expanded nodes
            :search \"\"         ; current search term
            :node-count n}}      ; total node count
   
   Node paths are dot-separated index strings (e.g. \"0\", \"0.2\", \"0.2.1\")
   identifying a node's position in the tree."
  (:require [clojure.string :as string]
            [dialog-tool.skein.trace :as trace]
            [dialog-tool.skein.ui.utils :refer [classes]]))

(defn- render-source-link
  "Renders a source reference as a clickable link that opens the source viewer
   in a new window, or as plain text if the source can't be parsed.
   node-path is the dot-separated tree path used to look up the source server-side."
  [source node-path]
  (if (trace/parse-source source)
    [:a.text-xs.text-blue-500.ml-auto.flex-shrink-0.font-mono.hover:text-blue-700.hover:underline
     {:href (str "/action/source/" node-path)
      :target "_blank"
      :title source}
     source]
    [:span.text-xs.text-gray-400.ml-auto.flex-shrink-0.font-mono
     source]))

(def ^:private type->badge-class
  {:enter "bg-blue-100 text-blue-800"
   :query "bg-purple-100 text-purple-800"
   :found "bg-green-100 text-green-800"
   :now "bg-amber-100 text-amber-800"})

(def ^:private type->label
  {:enter "ENTER"
   :query "QUERY"
   :found "FOUND"
   :now "NOW"})

(defn- render-node
  "Renders a single trace tree node and its visible children.
   path is the dot-separated index string for this node."
  [node path expanded]
  (let [{:keys [type predicate source children match?]} node
        has-children? (seq children)
        is-expanded? (contains? expanded path)]
    [:div.ml-4 {:key path}
     [:div.flex.items-center.gap-1.py-0.5.group.hover:bg-gray-50.rounded
      {:class (when match? "bg-yellow-100")}
      ;; Expand/collapse toggle
      (if has-children?
        [:button.w-5.h-5.flex.items-center.justify-center.text-gray-400.hover:text-gray-700.rounded.cursor-pointer.flex-shrink-0
         {:type "button"
          :data-on:click (str "@post('/action/trace/toggle/" path "')")}
         (if is-expanded? "▾" "▸")]
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
      [render-source-link source path]]
     ;; Children (only if expanded)
     (when (and has-children? is-expanded?)
       [:div
        (map-indexed
         (fn [idx child]
           (render-node child (str path "." idx) expanded))
         children)])]))

(defn render-trace-results
  "Renders just the trace tree results area (the part that updates on interactions).
   This has a stable `#trace-tree-results` ID so Datastar can patch it independently
   without disrupting the search input."
  [{:keys [tree expanded search node-count]}]
  (let [display-tree (if (string/blank? search)
                       tree
                       (trace/search-tree tree search))]
    [:div#trace-tree-results
     ;; Expand/collapse all controls + node count
     [:div.flex.items-center.gap-2.mb-2
      [:button.btn.btn-xs.btn-ghost
       {:type "button"
        :data-on:click "@post('/action/trace/expand-all')"}
       "Expand All"]
      [:button.btn.btn-xs.btn-ghost
       {:type "button"
        :data-on:click "@post('/action/trace/collapse-all')"}
       "Collapse All"]
      [:span.text-xs.text-gray-400.ml-auto
       (str node-count " nodes")]]
     ;; Tree view (scrollable)
     [:div.overflow-y-auto.border.rounded.p-2.bg-white
      {:class "max-h-[60vh]"}
      (map-indexed
       (fn [idx node]
         (render-node node (str idx) expanded))
       display-tree)]]))

(defn render-trace-tree
  "Renders the full trace tree view with search input and results.
   Used for the initial modal render only.
   
   trace-state is the :trace map from the session."
  [trace-state]
  [:div
   ;; Search bar (not re-rendered on interactions)
   [:div.mb-3.flex.items-center.gap-2
    [:input.input.input-bordered.input-sm.flex-1
     {:type "text"
      :placeholder "Search predicates or sources..."
      :data-bind "traceSearch"
      :data-on:keydown__debounce_300ms "@post('/action/trace/search')"
      :data-init "el.focus()"}]]
   ;; Results area (re-rendered independently)
   [render-trace-results trace-state]])

(defn collect-all-paths
  "Collects all node paths that have children, for use with expand-all."
  ([tree]
   (collect-all-paths tree "" #{}))
  ([nodes prefix result]
   (reduce
    (fn [acc [idx node]]
      (let [path (if (empty? prefix)
                   (str idx)
                   (str prefix "." idx))]
        (if (seq (:children node))
          (collect-all-paths (:children node) path (conj acc path))
          acc)))
    result
    (map-indexed vector nodes))))

(defn collect-matching-paths
  "Collects paths of all nodes with :has-match? true that have children.
   These are the paths that should be expanded to reveal search matches.
   The tree must have been processed by trace/search-tree first."
  ([tree]
   (collect-matching-paths tree "" #{}))
  ([nodes prefix result]
   (reduce
    (fn [acc [idx node]]
      (let [path (if (empty? prefix)
                   (str idx)
                   (str prefix "." idx))]
        (if (and (:has-match? node) (seq (:children node)))
          (collect-matching-paths (:children node) path (conj acc path))
          acc)))
    result
    (map-indexed vector nodes))))
