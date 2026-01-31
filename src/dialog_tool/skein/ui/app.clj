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

(defn navbar
  [title tree {:keys [can-undo? can-redo? dirty?]}]
  (let [{:keys [ok new error]} (tree/totals tree)
        labeled-knots (tree/labeled-knots-sorted tree)]
    [:nav {:class (classes "bg-white text-gray-500 border-gray-200 divide-gray-200"
                           "px-2 sm:px-4 py-2.5"
                           "fixed w-full z-20 top-0 start-0 border-b")}
     [:div.mx-auto.flex.flex-wrap.justify-between.items-center.container
      [:a.flex.items-center
       [:div.self-center.whitespace-nowrap.text-xl.font-semibold
        title]]
      [:div.mx-0.inline-flex
       [:div.text-black.bg-success.p-2.font-semibold.rounded-l-lg ok]
       [:div.text-black.bg-warning.p-2.font-semibold new]
       [:div.text-black.bg-error.p-2.font-semibold.rounded-r-lg.mr-2 error]
       [:div.flex.md:order-2.space-x-2
        [dropdown/dropdown {:disabled (<= (count labeled-knots) 1)
                            :label    [:<> svg/icon-jump "Jump"]}
         (for [{:keys [id label]} labeled-knots]
           [dropdown/button {:data-on:click (str "@get('/action/select/" id "')")}
            label])]
        [:div.btn.btn-primary {:data-on:click "@post('/action/replay-all')"}
         svg/icon-play "Replay All"]
        [:div.btn
         {:data-on:click "@post('/action/save')"
          :class         (if dirty? "btn-warning" "btn-primary")}
         svg/icon-save "Save"]
        [:div.btn.btn-primary {:data-on:click "@get('/action/undo')"
                               :disabled      (not can-undo?)}
         svg/icon-undo "Undo"]
        [:div.btn.btn-primary {:data-on:click "@get('/action/redo')"
                               :disabled      (not can-redo?)}
         svg/icon-redo "Redo"]
        [:div.btn.btn-primary {:data-on:click "@get('/action/quit')"}
         svg/icon-quit "Quit"]]]]]))

(def ^:private status->border-class
  {:ok    "border-slate-100"
   :new   "border-yellow-200"
   :error "border-rose-400"})

(def ^:private status->button-class
  {:ok    nil
   :new   "bg-warning"
   :error "bg-error"})

(defn- render-children-navigation
  [tree knot]
  (let [children (tree/children tree knot)
        {:keys [id]} knot]
    (when (< 1 (count children))
      [:div.indicator
       [dropdown/dropdown {:button-class (str "btn py-0 px-2 " (status->button-class (tree/descendant-status tree id)))
                           :label        svg/icon-children}
        (map (fn [{:keys [id label command]}]
               (let [status (tree/greatest-status (tree/knot-status tree id)
                                                  (tree/descendant-status tree id))]
                 [dropdown/button {:bg-class      (status->button-class status)
                                   :data-on:click (str "@get('/action/select/" id "')")}
                  (or label command)]))
             children)]
       [:div
        {:class (classes
                  "indicator-item indicator-top indicator-right"
                  "rounded-full text-sm border-2 bg-base-300 border-base-100"
                  "flex items-center justify-center"
                  "w-8 h-8")}
        (count children)]])))

(defn- render-knot
  [tree knot scroll-to-knot-id]
  (let [{:keys [id label response unblessed]} knot
        status         (tree/knot-status tree id)
        border-class   (status->border-class status)
        disable-bless? (= :ok status)
        root?          (zero? id)]
    [:div.border-x-4 (cond-> {:id    (str "knot-" id)
                              :class border-class}
                       (= id scroll-to-knot-id)
                       (assoc :data-scroll-into-view true))
     [:div.bg-yellow-50.w-full.whitespace-pre.relative.p-2
      [:div.whitespace-normal.flex.flex-row.absolute.top-2.right-2.gap-x-2
       (when label
         [:span.font-bold.bg-gray-200.p-1.rounded-md label])
       [dropdown/dropdown {:label        svg/icon-dots-vertical
                           :button-class "btn p-0"}
        [dropdown/button {:disabled      disable-bless?
                          :data-on:click (str "@post('/action/bless/" id "')")}
         "Bless" "Accept changes"]
        (when-not root?
          [dropdown/button {:disabled      disable-bless?
                            :data-on:click (str "@post('/action/bless-to/" id "')")}
           "Bless To Here" "Accept changes from root to here"])
        [dropdown/button {:data-on:click (str "@post('/action/replay-to/" id "')")}
         "Replay" "Run from start to here"]
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
       (render-children-navigation tree knot)]
      [render-diff response unblessed]
      [:hr.clear-right.text-stone-200]]]))


(defn render-app
  [request {:keys [scroll-to-new-command? reset-command-input? scroll-to-knot-id flash] :as _opts}]
  (let [{:keys [*session]} request
        session @*session
        {:keys [skein-path tree]} session
        knots   (tree/selected-knots tree)]
    [:div#app.relative.px-8
     (when flash
       [flash/flash-message flash])
     [navbar skein-path tree
      {:can-undo? (not-empty (:undo-stack session))
       :can-redo? (not-empty (:redo-stack session))
       :dirty?    (:dirty? session)}]
     [:div.container.mx-lg.mx-auto.mt-16
      (map (fn [knot]
             (render-knot tree knot scroll-to-knot-id)) knots)
      [new-command/new-command-input {:scroll-to?           scroll-to-new-command?
                                      :reset-command-input? reset-command-input?}]]]))
