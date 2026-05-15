(ns dialog-tool.skein.ui.trace-view
  "Renders a trace tree as interactive hiccup for the trace modal.
   
   The trace state is stored in the session as:
   {:trace {:nodes {id -> node}  ; flat node map (see trace ns)
            :search \"\"         ; current search term
            :node-count n}}      ; total visible node count
   
   Node 0 is an invisible root whose :children are the top-level node IDs."
  (:require [clojure.string :as string]
            [dialog-tool.skein.trace :as trace]
            [dialog-tool.skein.ui.utils :refer [classes]]
            [hyper.core :as h]))

(defn- render-source-link
  "Renders a source reference as a clickable link that opens the source viewer
   in a new window, or as plain text if the source can't be parsed.
   node-id is the node's unique numeric :id used to look up the source server-side."
  [source node-id]
  (if (trace/parse-source source)
    [:a.text-xs.text-blue-500.ml-auto.flex-shrink-0.font-mono.hover:text-blue-700.hover:underline
     {:href (str "/action/source/" node-id)
      :target "_blank"
      :title source
      :data-on:mouseenter__debounce_400ms (str "showSourcePreview(el,'" node-id "')")
      :data-on:mouseleave "hideSourcePreview()"}
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
   nodes is the flat node map, cursor is the session cursor,
   scroll-to-id is the optional :id to scroll into view."
  [nodes cursor node-id scroll-to-id]
  (let [{:keys [id type predicate source children match? expanded]} (get nodes node-id)
        has-children? (seq children)
        scroll-to? (= id scroll-to-id)]
    [:div.ml-4 {:key id}
     [:div.flex.items-center.gap-1.py-0.5.group.hover:bg-gray-50.rounded
      (cond-> {}
        match? (assoc :class "bg-yellow-100")
        scroll-to? (assoc :data-init "el.scrollIntoView({block:'center',behavior:'smooth'})"))
      ;; Expand/collapse toggle
      (if has-children?
        [:button.w-5.h-5.flex.items-center.justify-center.text-gray-400.hover:text-gray-700.rounded.cursor-pointer.flex-shrink-0
         {:type "button"
          :data-on:click (h/action
                          (swap! cursor update-in [:trace :nodes] trace/toggle-expanded id))}
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
          (render-node nodes cursor child-id scroll-to-id))])]))

(defn- apply-trace-search
  "Applies a search term to the trace state in the cursor."
  [cursor search]
  (swap! cursor
         (fn [session]
           (let [nodes (get-in session [:trace :nodes])
                 updated (if (string/blank? search)
                           (trace/collapse-all nodes)
                           (trace/expand-to-matches (trace/search-tree nodes search)))]
             (-> session
                 (assoc-in [:trace :search] search)
                 (assoc-in [:trace :nodes] updated))))))

(defn render-trace-tree
  "Renders the full trace tree view with search input, controls, and results.
   cursor is the session cursor, trace-state is the :trace map from the session."
  [cursor trace-state]
  (let [{:keys [nodes search node-count]} trace-state
        display-nodes (if (string/blank? search)
                        nodes
                        (trace/search-tree nodes search))
        root (get display-nodes 0)
        search-signal (h/local-signal :trace-search (or search ""))]
    (list
     ;; Search bar
     [:div.mb-3.flex.items-center.gap-2.flex-shrink-0
      [:input.input.input-bordered.input-sm.flex-1
       {:type "text"
        :placeholder "Search predicates or sources..."
        :data-bind (:name search-signal)
        :data-on:keydown__debounce_300ms
        (h/action
         (let [search (or $value "")]
           (apply-trace-search cursor search)
           (when (= $key "Enter")
             ;; Scroll to first matching node
             (let [nodes (get-in @cursor [:trace :nodes])]
               (when-let [match-id (trace/find-first-match nodes)]
                 (swap! cursor assoc-in [:trace :scroll-to] match-id))))))
        :data-init "el.focus()"}]]
     ;; Expand/collapse all controls + node count
     [:div.flex.items-center.gap-2.mb-2.flex-shrink-0
      [:button.btn.btn-xs.btn-ghost
       {:type "button"
        :data-on:click (h/action
                        (swap! cursor update-in [:trace :nodes] trace/expand-all))}
       "Expand All"]
      [:button.btn.btn-xs.btn-ghost
       {:type "button"
        :data-on:click (h/action
                        (swap! cursor update-in [:trace :nodes] trace/collapse-all))}
       "Collapse All"]
      [:span.text-xs.text-gray-400.ml-auto
       (str node-count " nodes")]]
     ;; Tree view (scrollable)
     [:div.flex-1.overflow-y-auto.border.rounded.p-2.bg-white
      (for [child-id (:children root)]
        (render-node display-nodes cursor child-id (:scroll-to trace-state)))])))
