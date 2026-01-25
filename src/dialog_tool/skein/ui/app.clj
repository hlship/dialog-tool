(ns dialog-tool.skein.ui.app
  (:require [dialog-tool.skein.ui.svg :as svg]
            [dialog-tool.skein.ui.utils :refer [classes]]
            [dialog-tool.skein.ui.components.dropdown :as dropdown]
            [dialog-tool.skein.ui.components.new-command :as new-command]
            [dialog-tool.skein.ui.components.flash :as flash]
            [dialog-tool.skein.ui.diff :as diff]
            [dialog-tool.skein.tree :as tree]))

(defn render-diff
  "Render the difference between response and unblessed as hiccup markup.
   When unblessed is nil, just renders the response as-is.
   When unblessed is present, shows word-level diff:
   - Removed text (in response but not unblessed): red strikethrough
   - Added text (in unblessed but not response): blue bold
   - Unchanged text: normal styling"
  [response unblessed]
  (if unblessed
    (let [changes (diff/compute-diff response unblessed)]
      (into [:<>]
            (map (fn [{:keys [type value]}]
                   (case type
                     :added [:span.text-blue-700.font-bold value]
                     :removed [:span.text-red-800.font-bold.line-through value]
                     :unchanged value)))
            changes))
    ;; No unblessed, just show response as-is
    response))

(def button-base
  (classes "text-center font-medium"
           "focus-within:ring-4 focus-within:outline-none"
           "inline-flex items-center"
           "justify-center px-3 py-2 text-xs text-white"
           "rounded-lg ml-8"))

(def blue-button
  (str button-base " bg-blue-700 hover:bg-blue-800"))

(def disabled-button
  (str button-base " bg-blue-400 cursor-not-allowed"))

(defn nav-button [attrs body]
  (let [disabled? (:disabled attrs)]
    [:button (merge {:type "button"
                     :class (if disabled? disabled-button blue-button)}
                    attrs)
     body]))

(defn navbar
  [title tree can-undo? can-redo?]
  (let [{:keys [ok new error]} (tree/counts tree)
        {:keys [dirty?]} tree]
    [:nav {:class (classes "bg-white text-gray-500 border-gray-200 divide-gray-200"
                           "px-2 sm:px-4 py-2.5"
                           "fixed w-full z-20 top-0 start-0 border-b")}
     [:div.mx-auto.flex.flex-wrap.justify-between.items-center.container
      [:a.flex.items-center
       [:div.self-center.whitespace-nowrap.text-xl.font-semibold
        title]]
      [:div.mx-0.inline-flex
       [:div.text-black.bg-green-400.p-2.font-semibold.rounded-l-lg ok]
       [:div.text-black.bg-yellow-200.p-2.font-semibold new]
       [:div.text-black.bg-red-500.p-2.font-semibold.rounded-r-lg error]
       (let [labeled-knots (tree/labeled-knots-sorted tree)]
         [dropdown/dropdown {:id "jump-dropdown"
                             :button-class blue-button
                             :disabled (<= (count labeled-knots) 1)
                             :label [:<> svg/icon-jump "Jump"]}
          (map (fn [{:keys [id label]}]
                 [dropdown/button {:data-on:click (str "@get('/action/select/" id "')")}
                  label])
               labeled-knots)])
       [:div.flex.md:order-2.space-x-2
        [nav-button {:data-on:click "@post('/action/replay-all')"} [:<> svg/icon-play "Replay All"]]
        [nav-button {:data-on:click "@post('/action/save')"
                     :class (classes button-base
                                     (if dirty?
                                       "bg-green-700 hover:bg-green-800"
                                       "bg-blue-700 hover:bg-blue-800"))}
         [:<> svg/icon-save "Save"]]
        [nav-button {:data-on:click "@get('/action/undo')"
                     :disabled (not can-undo?)}
         [:<> svg/icon-undo "Undo"]]
        [nav-button {:data-on:click "@get('/action/redo')"
                     :disabled (not can-redo?)}
         [:<> svg/icon-redo "Redo"]]
        [nav-button nil [:<> svg/icon-quit "Quit"]]]]]]))

(def ^:private category->border-class
  {:ok "border-slate-100"
   :new "border-yellow-200"
   :error "border-rose-400"})

(defn- render-children-navigation
  [tree knot descendant-status]
  (let [children (tree/children tree knot)
        {:keys [id]} knot]
    (when (seq children)
      (let [;; Check descendant status for each child to find worst status
            child-statuses (map #(descendant-status (:id %)) children)
            has-error? (some #{:error} child-statuses)
            has-new? (some #{:new} child-statuses)
            bg-class (cond
                       has-error? "bg-red-500"
                       has-new? "bg-yellow-200"
                       :else "bg-white")]
        [dropdown/dropdown {:id (str "nav-" id)
                            :bg-class bg-class
                            :label [:<> svg/icon-children
                                    (when (< 1 (count children))
                                      [:div
                                       {:class (classes
                                                "flex-shrink-0 rounded-full border-2 border-white"
                                                "w-6 h-6 bg-gray-200 inline-flex items-center justify-center"
                                                "absolute top-0 end-0"
                                                "translate-x-1/3 -translate-y-1/3"
                                                "hover:bg-slate-200")}
                                       (count children)])]}
         (map (fn [{:keys [id label command]}]
                [dropdown/button {:data-on:click (str "@get('/action/select/" id "')")}
                 (or label command)])
              children)]))))

(defn- render-knot
  [tree knot enable-bless-to? descendant-status scroll-to-knot-id]
  (let [{:keys [id label response unblessed]} knot
        category (tree/assess-knot knot)
        border-class (category->border-class category)
        root? (zero? id)]
    [:div.border-x-4 (cond-> {:id (str "knot-" id)
                              :class border-class}
                       (= id scroll-to-knot-id)
                       (assoc :data-scroll-into-view true))
     [:div.bg-yellow-50.w-full.whitespace-pre.relative.p-2
      [:div.whitespace-normal.flex.flex-row.absolute.top-2.right-2.gap-x-2
       (when label
         [:span.font-bold.bg-gray-200.p-1.rounded-md label])
       [dropdown/dropdown {:id (str "actions-" id)
                           :label svg/icon-dots-vertical}
        [dropdown/button {:disabled (= category :ok)
                          :data-on:click (str "@post('/action/bless/" id "')")}
         "Bless" "Accept changes"]
        (when-not root?
          [dropdown/button {:disabled (not enable-bless-to?)
                            :data-on:click (str "@post('/action/bless-to/" id "')")}
           "Bless To Here" "Accept changes from root to here"])
        [dropdown/button nil "Replay" "Run from start to here"]
        [dropdown/button {:data-on:click (str "@post('/action/new-child/" id "')")}
         "New Child" "Add a new command after this"]
        (when-not root?
          [:<>
           [dropdown/button {:data-on:click (str "@get('/action/edit-label/" id "')")}
            "Edit Label" "Change label for knot"]
           [dropdown/button {:data-on:click (str "@get('/action/edit-command/" id "')")}
            "Edit Command" "Change the knot's command"]
           [dropdown/button {:data-on:click (str "@get('/action/insert-parent/" id "')")}
            "Insert Parent" "Insert a command before this"]
           [dropdown/button {:data-on:click (str "@post('/action/delete/" id "')")}
            "Delete" "Delete this knot and all children"]
           [dropdown/button {:data-on:click (str "@post('/action/splice-out/" id "')")}
            "Splice Out" "Delete this knot, reparent children up"]])]
       (render-children-navigation tree knot descendant-status)]
      [render-diff response unblessed]
      [:hr.clear-right.text-stone-200]]]))

(defn- compute-bless-to-flags
  "Returns a seq of [knot, enable-bless-to?] pairs. enable-bless-to? is true if any knot
   from root to this knot (inclusive) is not :ok."
  [knots]
  (second
   (reduce (fn [[any-not-ok? result] knot]
             (let [not-ok? (not= :ok (tree/assess-knot knot))
                   any-not-ok?' (or any-not-ok? not-ok?)]
               [any-not-ok?' (conj result [knot any-not-ok?'])]))
           [false []]
           knots)))

(defn render-app
  [request {:keys [scroll-to-new-command? reset-command-input? scroll-to-knot-id flash] :as _opts}]
  (let [{:keys [*session]} request
        session @*session
        {:keys [skein-path tree]} session
        knots-with-flags (-> tree tree/selected-knots compute-bless-to-flags)
        descendant-status (tree/compute-descendant-status tree)]
    [:div#app.relative.px-8
     (when flash
       [flash/flash-message flash])
     [navbar skein-path tree
      (not-empty (:undo-stack session))
      (not-empty (:redo-stack session))]
     [:div.container.mx-lg.mx-auto.mt-16
      (map (fn [[knot enable-bless-to?]] (render-knot tree knot enable-bless-to? descendant-status scroll-to-knot-id)) knots-with-flags)
      [new-command/new-command-input {:scroll-to? scroll-to-new-command?
                                      :reset-command-input? reset-command-input?}]
      ;; TODO: This should only show when in some kind of development mode
      [:div.fixed.top-4.left-4.bg-gray-800.text-white.p-3.rounded-lg.shadow-lg.max-w-md.max-h-64.overflow-auto.z-50.text-xs
       [:pre.whitespace-pre-wrap {:data-json-signals true}]]]]))
