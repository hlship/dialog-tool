(ns dialog-tool.skein.ui.app
  (:require [clojure.string :as string]
            [dialog-tool.skein.dynamic :as dynamic]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.ui.ansi :as ansi]
            [dialog-tool.skein.ui.components.dropdown :as dropdown]
            [dialog-tool.skein.ui.components.flash :as flash]
            [dialog-tool.skein.ui.components.new-command :as new-command]
            [dialog-tool.skein.ui.diff :as diff]
            [dialog-tool.skein.ui.modals :as modals]
            [dialog-tool.skein.ui.utils :refer [classes]]
            [hyper.core :as h]))

(defn- visible-whitespace
  "Replaces whitespace characters with visible alternates for use in diff segments.
   Spaces become middle-dots (·) and newlines become ↵ followed by the actual newline."
  [s]
  (-> s
      (string/replace " " "·")
      (string/replace "\n" "↵\n")))

(defn render-diff
  "Render the difference between response and unblessed as hiccup markup.
   When unblessed is nil, converts ANSI codes to styled HTML spans.
   When unblessed is present, converts ANSI codes to visible pseudo-markers
   (e.g. [B], [BLUE]) then shows word-level diff."
  [response unblessed]
  (if unblessed
    (let [response (ansi/ansi->markers response)
          unblessed (ansi/ansi->markers unblessed)
          changes (diff/diff-text response unblessed)]
      (map (fn [{:keys [type value]}]
             (case type
               :added [:span.text-blue-700.font-bold (visible-whitespace value)]
               :removed [:span.text-red-800.font-bold.line-through (visible-whitespace value)]
               :unchanged value))
           changes))
    ;; No unblessed, render with ANSI styling
    (ansi/ansi->hiccup response)))

(defn- flash!
  "Sets a flash message on the cursor and schedules it to auto-clear after 3 seconds."
  [cursor message]
  (swap! cursor assoc :flash message)
  (future
    (Thread/sleep 3000)
    (swap! cursor dissoc :flash)))

(defn- jump-to-status!
  [cursor status]
  (let [session @cursor
        tree (:tree session)
        matching (tree/knots-with-status tree status)]
    (when (seq matching)
      (let [last-id (get-in session [:last-jump status])
            idx (when last-id
                  (let [i (.indexOf matching last-id)]
                    (when (>= i 0) i)))
            next-idx (if idx
                       (mod (inc idx) (count matching))
                       0)
            next-id (nth matching next-idx)]
        (swap! cursor assoc-in [:last-jump status] next-id)
        (swap! cursor session/select-knot next-id)))))

(defn- replay-all!
  [cursor]
  (let [initial-tree (:tree @cursor)
        leaf-knots (tree/leaf-knots initial-tree)]
    (swap! cursor session/capture-undo)
    (swap! cursor session/check-for-changed-sources)
    (swap! cursor assoc :continue true)
    (doseq [[idx knot] (map-indexed vector leaf-knots)
            :while (:continue @cursor)]
      (swap! cursor assoc :progress {:current (inc idx)
                                     :total (count leaf-knots)
                                     :label (:label knot)
                                     :operation "Replaying All"})
      (swap! cursor #(session/do-replay-to! % (:id knot))))
    (let [cancelled? (not (:continue @cursor))]
      (swap! cursor dissoc :continue :progress)
      (flash! cursor (if cancelled? "Replay cancelled" "Replay complete")))))

(defn navbar
  [cursor session]
  (let [{:keys [skein-path tree dirty?]} session
        can-undo? (-> session :undo-stack not-empty)
        can-redo? (-> session :redo-stack not-empty)
        {:keys [ok new error]} (tree/totals tree)
        labeled-knots (tree/labeled-knots-sorted tree)]
    [:nav {:class (classes "bg-white text-gray-500 border-gray-200 divide-gray-200"
                           "px-2 sm:px-4 py-2.5"
                           "fixed w-full z-20 top-0 start-0 border-b")}
     [:div.mx-auto.flex.items-center.gap-2.container
      [:div.self-center.truncate.text-xl.font-semibold.shrink.min-w-0
       skein-path]
      [:div.join.shrink-0.mx-auto
       [:div.text-black.bg-success.p-2.font-semibold.rounded-l-lg ok]
       [:div.text-black.bg-warning.p-2.font-semibold
        (when (pos? new)
          {:class "cursor-pointer"
           :data-on:click (h/action (jump-to-status! cursor :new))})
        new]
       [:div.text-black.bg-error.p-2.font-semibold.rounded-r-lg
        (when (pos? error)
          {:class "cursor-pointer"
           :data-on:click (h/action (jump-to-status! cursor :error))})
        error]]
      [:div.flex.items-center.gap-1.shrink-0.ml-auto
       (dropdown/dropdown {:disabled (<= (count labeled-knots) 1)
                           :label (list [:div.icon.icon-jump] [:span.hidden.lg:inline "Jump"])}
                          (for [{:keys [id label]} labeled-knots]
                            (dropdown/button {:data-on:click (h/action (swap! cursor session/select-knot id))}
                                             label)))
       [:div.btn.btn-primary.tooltip.tooltip-bottom
        {:data-on:click (h/action (replay-all! cursor))
         :data-accel "p"
         :data-preserve-attr "data-tip"}
        [:div.icon.icon-play] [:span.hidden.lg:inline "Replay All"]]
       [:div.btn.btn-primary.tooltip.tooltip-bottom
        {:data-on:click (h/action
                         (swap! cursor session/save!)
                         (flash! cursor "Saved"))
         :data-accel "s"
         :data-preserve-attr "data-tip"
         :class (when dirty? "btn-soft")}
        [:div.icon.icon-save] [:span.hidden.lg:inline "Save"]]
       [:div.btn.btn-primary.tooltip.tooltip-bottom
        {:data-on:click (h/action
                         (swap! cursor session/undo)
                         (flash! cursor "Undo"))
         :data-accel "z"
         :data-preserve-attr "data-tip"
         :disabled (not can-undo?)}
        [:div.icon.icon-undo] [:span.hidden.lg:inline "Undo"]]
       [:div.btn.btn-primary.tooltip.tooltip-bottom
        {:data-on:click (h/action
                         (swap! cursor session/redo)
                         (flash! cursor "Redo"))
         :data-accel__shift "z"
         :data-preserve-attr "data-tip"
         :disabled (not can-redo?)}
        [:div.icon.icon-redo] [:span.hidden.lg:inline "Redo"]]
       [:div.btn.btn-primary {:data-on:click (h/action
                                              (if (:dirty? @cursor)
                                                (swap! cursor assoc :modal {:type :quit})))}
                                                ;; TODO: actual shutdown when not dirty

        [:div.icon.icon-quit] [:span.hidden.lg:inline "Quit"]]]]]))

(def ^:private status->border-class
  {:ok "border-slate-100"
   :new "border-yellow-200"
   :error "border-rose-400"})

(def ^:private status->button-class
  {:ok nil
   :new "bg-warning"
   :error "bg-error"})

(defn- compare-pred
  [left right]
  (compare (string/replace left "#" "")
           (string/replace right "#" "")))

(defn- render-dynamic
  "Renders the dynamic state."
  [tree knot]
  (let [{:keys [parent-id dynamic-state]} knot
        before-dynamic-state (-> (tree/get-knot tree parent-id) :dynamic-state)]
    (when (and (seq dynamic-state)
               (seq before-dynamic-state))
      (let [{:keys [added removed changed]} (dynamic/diff before-dynamic-state dynamic-state)
            tuples (->> []
                        (into (map #(vector :added %) added))
                        (into (map #(vector :removed %) removed))
                        (into (map #(vector :changed (second %)) changed))
                        (sort-by second compare-pred))]
        (when (seq tuples)
          [:div.font-sans.flex.flex-wrap.gap-1.mt-4.text-xs
           (for [[kind predicate] tuples]
             (case kind
               :added [:span.rounded-box.border.border-success.bg-success.bg-opacity-20.text-success-content.px-2.py-1
                       [:span.font-bold.mr-1 "+"] predicate]
               :removed [:span.rounded-box.border.border-warning.bg-warning.bg-opacity-20.text-warning-content.px-2.py-1
                         [:span.font-bold.mr-1 "−"] predicate]
               :changed [:span.rounded-box.border.border-info.bg-info.bg-opacity-10.px-2.py-1
                         predicate]))])))))
(defn- render-children-navigation
  [cursor tree knot]
  (let [children (tree/children tree knot)
        {:keys [descendant-status]} knot]
    [:div.indicator
     (dropdown/dropdown {:button-class (str "btn py-0 px-2 " (status->button-class descendant-status))
                         :disabled (< (count children) 2)
                         :label [:div.icon.icon-children]}
                        (map (fn [{:keys [id label command]}]
                               (let [status (tree/greatest-status (tree/knot-status tree id)
                                                                  (tree/descendant-status tree id))]
                                 (dropdown/button {:bg-class (status->button-class status)
                                                   :data-on:click (h/action (swap! cursor session/select-knot id))}
                                                  (or label command))))
                             children))
     (when (> (count children) 1)
       [:div
        {:class (classes
                 "indicator-item indicator-top indicator-right"
                 "rounded-full text-sm border-2 bg-base-300 border-base-100"
                 "flex items-center justify-center"
                 "w-8 h-8")}
        (count children)])]))

(defn- render-knot
  [cursor tree knot {:keys [debug-enabled? show-dynamic? fixed-width?]}]
  (let [{:keys [id label response unblessed status children dynamic-response locked]} knot
        border-class (status->border-class status)
        disable-bless? (= :ok status)
        root? (= 0 id)]
    [:div.border-x-4 {:id (str "knot-" id)
                      :class border-class}
     [:div.bg-yellow-50.w-full.whitespace-pre-wrap.p-2
      {:class (when (or fixed-width? (= :error status)) "font-mono")}
      [:div.whitespace-normal.font-sans.flex.flex-row.items-center.gap-x-2.float-right.sticky.top-16.bg-yellow-50.rounded-bl-lg.pl-2.pb-1
       (when locked
         [:div.icon.icon-lock {:title "Locked"}])
       (when label
         [:span.font-bold.bg-gray-200.p-1.rounded-md label])
       (dropdown/dropdown {:label [:div.icon.icon-dots-vertical]
                           :button-class "btn p-0"}
                          (dropdown/button {:disabled disable-bless?
                                            :data-on:click (h/action
                                                            (swap! cursor session/bless id)
                                                            (flash! cursor "Blessed"))}
                                           "Bless" "Accept changes")
                          (when-not root?
                            (dropdown/button {:disabled disable-bless?
                                              :data-on:click (h/action
                                                              (swap! cursor session/bless-to id)
                                                              (flash! cursor "Blessed to here"))}
                                             "Bless To Here" "Accept changes from root to here"))
                          (dropdown/button {:data-on:click (h/action
                                                            (swap! cursor session/check-for-changed-sources)
                                                            (swap! cursor session/replay-to! id)
                                                            (flash! cursor "Replayed"))}
                                           "Replay" "Run from start to here")
                          (dropdown/button {:data-on:click (h/action
                                                            (swap! cursor session/prepare-new-child! id))}
                                           "New Child" "Add a new command after this")
                          (when-not root?
                            (list
                             (dropdown/button {:data-on:click (h/action
                                                               (swap! cursor assoc :modal
                                                                      {:type :edit-command :knot-id id}))}
                                              "Edit Command ..." "Change the knot's command")
                             (dropdown/button {:data-on:click (h/action
                                                               (swap! cursor assoc :modal
                                                                      {:type :edit-label :knot-id id}))}
                                              "Edit Label ..." "Change label for knot")
                             (dropdown/button {:data-on:click (h/action
                                                               (swap! cursor session/toggle-lock id)
                                                               (let [locked? (get-in @cursor [:tree :knots id :locked])]
                                                                 (flash! cursor (if locked? "Locked" "Unlocked"))))}
                                              "Toggle Lock" "Lock or unlock this knot")
                             (dropdown/button {:data-on:click (h/action
                                                               (swap! cursor assoc :modal
                                                                      {:type :insert-parent :knot-id id}))}
                                              "Insert Parent" "Insert a command before this")
                             (dropdown/button {:data-on:click (h/action
                                                               (let [[error session'] (session/delete! @cursor id)]
                                                                 (reset! cursor session')
                                                                 (flash! cursor
                                                                         (if error
                                                                           {:message error :type :error}
                                                                           "Deleted"))))}
                                              "Delete" "Delete this knot and all children")
                             (dropdown/button {:disabled (not (seq children))
                                               :data-on:click (h/action
                                                               (let [[error session'] (session/splice-out! @cursor id)]
                                                                 (reset! cursor session')
                                                                 (flash! cursor
                                                                         (if error
                                                                           {:message error :type :error}
                                                                           "Spliced out"))))}
                                              "Splice Out" "Delete this knot, reparent children up")))
                          (dropdown/button {:disabled (nil? dynamic-response)
                                            :data-on:click (h/action
                                                            (swap! cursor assoc :modal
                                                                   {:type :dynamic-state :knot-id id}))}
                                           "Dynamic State ..."
                                           "Show full dynamic state")
                                                                              (when debug-enabled?
                                                                                (dropdown/button {:data-on:click (h/action
                                                                                                                  nil ;; TODO: trace support
                                                                                                                  )}
                                                                                                 "Trace ..."
                                                                                                 (if root? "Trace startup" "Trace command execution"))))
       (render-children-navigation cursor tree knot)]
      (render-diff response unblessed)
      [:hr.clear-right.text-stone-200]
      (when (and debug-enabled? show-dynamic?
                 (not= 0 id))
        (render-dynamic tree knot))]]))

(defn- render-fab
  [cursor session]
  (let [{:keys [debug-enabled? show-dynamic? fixed-width?]} session]
    [:div.fab#fab
     [:div.btn.btn-lg.btn-circle.btn-primary
      {:tabindex "0"
       :role "button"}
      [:div.icon.icon-globe]]

     [:div.rounded-box.bg-primary-content.flex.flex-col.items-start
      [:label.label.p-2
       [:input.toggle {:type "checkbox"
                       :checked fixed-width?
                       :data-on:change (h/action
                                        (swap! cursor update :fixed-width? not))}]
       "Fixed-width font"]
      [:label.label.p-2
       [:input.toggle {:type "checkbox"
                       :checked show-dynamic?
                       :disabled (not debug-enabled?)
                       :data-on:change (h/action
                                        (swap! cursor update :show-dynamic? not))}]
       "Show dynamic state"]]]))

(defn- render-modal
  "Renders the appropriate modal based on the :modal key in the session."
  [cursor session]
  (let [{:keys [modal tree progress]} session]
    (cond
      progress
      (modals/progress cursor progress)

      modal
      (let [{:keys [type knot-id error]} modal
            knot (when knot-id (tree/get-knot tree knot-id))]
        (case type
          :edit-command
          (modals/edit-command cursor knot-id (:command knot) error)

          :edit-label
          (modals/edit-label cursor knot-id (or (:label knot) "") (boolean (:locked knot)) error)

          :insert-parent
          (modals/insert-parent cursor knot-id "" error)

          :dynamic-state
          (modals/dynamic-state cursor (:dynamic-response knot))

          :quit
          (modals/quit-modal cursor)

          nil)))))

(defn skein-page
  "Main hyper page function. Renders the full skein UI from the session cursor.
  Hyper calls this whenever the cursor changes and pushes the diff via SSE."
  [req]
  (let [cursor (h/global-cursor :session)
        session @cursor
        {:keys [tree debug-enabled? show-dynamic? fixed-width? flash]} session
        knots (tree/selected-knots tree)
        leaf-knot (last knots)]
    (h/watch! cursor)
    [:div.relative.px-8
     (when flash
       (flash/flash-message flash))
     (navbar cursor session)
     [:div.container.mx-lg.mx-auto.mt-16
      (map (fn [knot]
             (render-knot cursor tree knot {:debug-enabled? debug-enabled?
                                            :show-dynamic? show-dynamic?
                                            :fixed-width? fixed-width?}))
           knots)
      (new-command/new-command-input cursor (:id leaf-knot))]
     ;; Modal overlay
     (render-modal cursor session)
     ;; FAB for settings
     (render-fab cursor session)]))