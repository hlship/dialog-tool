(ns dialog-tool.skein.ui.app
  (:require [clojure.string :as string]
            [dialog-tool.skein.dynamic :as dynamic]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.trace :as trace]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.ui.ansi :as ansi]
            [dialog-tool.skein.ui.common :as common]
            [dialog-tool.skein.ui.components.dropdown :as dropdown]
            [dialog-tool.skein.ui.components.flash :as flash]
            [dialog-tool.skein.ui.components.new-command :as new-command]
            [dialog-tool.skein.ui.diff :as diff]
            [dialog-tool.skein.ui.modals :as modals]
            [dialog-tool.skein.ui.utils :refer [classes]]
            [hyper.core :as h]
            [hyper.effects :as effects]))

(defn- visible-whitespace
  "Replaces whitespace characters with visible alternates for use in diff segments.
   Spaces become middle-dots (·) followed by a zero-width space so the browser
   retains a soft wrap opportunity. Newlines become ↵ followed by the actual newline."
  [s]
  (-> s
      (string/replace " " "·\u200B")
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
               :added [:span.text-info.font-bold (visible-whitespace value)]
               :removed [:span.text-error.font-bold.line-through (visible-whitespace value)]
               :unchanged value))
           changes))
    ;; No unblessed, render with ANSI styling
    (ansi/ansi->hiccup response)))

(defn shutdown!
  "Shows a close message, then shuts down after the browser receives the update.
  The shutdown-fn is stored in app-state at [:global :shutdown-fn] by the service."
  [cursor *app-state]
  ;; Set closing state so the page renders the close message
  (swap! cursor assoc :closing? true)
  (future
    ;; Give hyper time to push the closing message to the browser
    (Thread/sleep 500)
    ((get-in @*app-state [:global :shutdown-fn]))))

;; Pending flash message — stored outside the session cursor so it doesn't
;; persist across re-renders. The render function consumes and clears it.
;; TODO: Never happy to have a global like this, there must be a better way
;; to deal with this.
(def ^:private *pending-flash (atom nil))

(defn- flash!
  "Queues a flash message to be shown on the next render.
  Does not modify the session cursor — the render function reads and clears this."
  [message]
  (reset! *pending-flash message))

(defn- scroll-knot-into-view!
  "Emits a JS effect that smoothly scrolls the given knot into view."
  [knot-id]
  (effects/execute-script!
   (str "document.getElementById('knot-" knot-id "')?.scrollIntoView({block:'nearest',behavior:'smooth'})")))

(defn- reset-and-focus-command-input!
  "Clears the command input, scrolls it into view, and focuses it."
  []
  (effects/execute-script!
   (str "var el=document.getElementById('new-command-input');"
        "if(el){"
        "el.value='';"
        "el.dispatchEvent(new Event('input',{bubbles:true}));"
        "el.scrollIntoView({block:'nearest',behavior:'smooth'});"
        "el.focus({preventScroll:true});"
        "}")))

(defn- focus-if-leaf!
  "Focuses the command input if knot-id is the leaf of the selected path
  (i.e. has no selected child)."
  [cursor knot-id]
  (when (nil? (get-in @cursor [:tree :selected knot-id]))
    (reset-and-focus-command-input!)))

(defn- navigate-to-active-knot!
  "After undo/redo: focuses the command input if the active knot is the leaf,
  otherwise scrolls it into view."
  [cursor]
  (let [active-id (get-in @cursor [:tree :active-knot-id])]
    (if (nil? (get-in @cursor [:tree :selected active-id]))
      (reset-and-focus-command-input!)
      (scroll-knot-into-view! active-id))))

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
        (swap! cursor session/select-knot next-id)
        (swap! cursor session/set-active-knot next-id)))))

(defn- swap-session!
  "Swaps the session in the app-state atom. Works from any thread."
  ([*app-state f]
   (swap! *app-state update-in [:global :session] f))
  ([*app-state f & args]
   (apply swap! *app-state update-in [:global :session] f args)))

(defn- reset-session!
  [*app-state session]
  (swap! *app-state assoc-in [:global :session] session))

(defn- set-progress!
  "Updates the progress atom in app-state. Only the progress modal watches this."
  [*app-state progress]
  (swap! *app-state assoc-in [:global :progress] progress))

(defn- complete-session-operation
  "Invoked after a command that operates on the process; if there was a startup error
  after restarting the process (because of a source code error) then sets up the source error
  modal dialog.
  
  If there's no error, updates the flash message."
  [session flash-message]
  (let [{:keys [error]} session]
    (when-not error
      (reset! *pending-flash flash-message))
    (cond-> session
      error (common/setup-source-error error))))

(defn replay-to!
  "Replays to a specific knot."
  [req]
  (let [{*app-state :hyper/app-state} req
        knot-id (get-in req [:hyper/route :path-params :id])]
    (swap-session! *app-state
                   (fn [session]
                     (-> session
                         session/check-for-changed-sources
                         (session/replay-to! knot-id)
                         (complete-session-operation "Replayed"))))
    {:status 200}))

(defn replay-all!
  "Replays all leaf knots. Progress is tracked in a separate :progress key
  so that progress updates don't trigger full page re-renders.
  The session is accumulated locally and only committed at the end."
  [req]
  (let [{*app-state :hyper/app-state} req
        ;; Ugly: this is to dismiss the source error modal dialog if the user clicks the "Replay All"
        ;; button on it.
        app-state (swap-session! *app-state dissoc :modal)
        session (-> app-state
                    :global
                    :session
                    session/capture-undo
                    session/check-for-changed-sources)
        leaf-knots (tree/leaf-knots (:tree session))
        total (count leaf-knots)
        cancelled? #(not (get-in @*app-state [:global :progress :continue]))]
    ;; Store continue flag in progress so cancel can stop the loop
    (set-progress! *app-state {:continue true})
    (let [session' (reduce (fn [session [idx knot]]
                             (if (or (cancelled?)
                                     (:error session))
                               (reduced session)
                               (do
                                 (set-progress! *app-state
                                                {:current (inc idx)
                                                 :total total
                                                 :label (:label knot)
                                                 :operation "Replaying All"
                                                 :continue true})
                                 (session/do-replay-to! session (:id knot)))))
                           session
                           (map-indexed vector leaf-knots))]
      ;;  Dismiss the progress dialog
      (reset-session! *app-state
                      (complete-session-operation session'
                                                  (if (cancelled?) "Replay cancelled" "Replay complete")))
      (set-progress! *app-state nil)))
  {:status 200})

(defn navbar
  [cursor session *app-state]
  (let [{:keys [skein-path tree dirty?]} session
        can-undo? (-> session :undo-stack not-empty)
        can-redo? (-> session :redo-stack not-empty)
        {:keys [ok new error]} (tree/totals tree)
        labeled-knots (tree/labeled-knots-sorted tree)]
    [:nav {:class (classes "bg-base-100 text-base-content border-base-200 divide-base-200"
                           "px-2 sm:px-4 py-2.5"
                           "w-full border-b")}
     [:div.mx-auto.flex.items-center.gap-2.container
      [:div.self-center.truncate.text-xl.font-semibold.shrink.min-w-0
       skein-path]
      [:div.join.shrink-0.mx-auto
       [:div.bg-success.text-success-content.p-2.font-semibold.rounded-l-lg ok]
       [:div.bg-warning.text-warning-content.p-2.font-semibold
        (when (pos? new)
          {:class "cursor-pointer"
           :data-on:click (h/action
                            (jump-to-status! cursor :new)
                            (focus-if-leaf! cursor (get-in @cursor [:tree :active-knot-id])))})
        new]
       [:div.bg-error.text-error-content.p-2.font-semibold.rounded-r-lg
        (when (pos? error)
          {:class "cursor-pointer"
           :data-on:click (h/action
                            (jump-to-status! cursor :error)
                            (focus-if-leaf! cursor (get-in @cursor [:tree :active-knot-id])))})
        error]]
      [:div.flex.items-center.gap-1.shrink-0.ml-auto
       (dropdown/dropdown {:disabled (<= (count labeled-knots) 1)
                           :label (list [:div.icon.icon-jump] [:span.hidden.lg:inline "Jump"])}
                          (for [{:keys [id label]} labeled-knots]
                            (dropdown/button {:data-on:click (h/action
                                                              (swap! cursor session/select-knot id)
                                                              (swap! cursor session/set-active-knot id)
                                                              (focus-if-leaf! cursor id))}
                                             label)))
       [:div.btn.btn-primary.tooltip.tooltip-bottom
        {:data-on:click "@post('/action/replay-all')"
         :data-accel__shift "y"
         :data-preserve-attr "data-tip"}
        [:div.icon.icon-play] [:span.hidden.lg:inline "Replay All"]]
       [:div.btn.btn-primary.tooltip.tooltip-bottom
        {:data-on:click (h/action
                         (swap! cursor session/save!)
                         (flash! "Saved"))
         :data-accel "s"
         :data-preserve-attr "data-tip"
         :class (when dirty? "btn-soft")}
        [:div.icon.icon-save] [:span.hidden.lg:inline "Save"]]
       [:div.btn.btn-primary.tooltip.tooltip-bottom
        {:data-on:click (h/action
                         (swap! cursor session/undo)
                         (flash! "Undo")
                         (navigate-to-active-knot! cursor))
         :data-accel "z"
         :data-preserve-attr "data-tip"
         :disabled (not can-undo?)}
        [:div.icon.icon-undo] [:span.hidden.lg:inline "Undo"]]
       [:div.btn.btn-primary.tooltip.tooltip-bottom
        {:data-on:click (h/action
                         (swap! cursor session/redo)
                         (flash! "Redo")
                         (navigate-to-active-knot! cursor))
         :data-accel__shift "z"
         :data-preserve-attr "data-tip"
         :disabled (not can-redo?)}
        [:div.icon.icon-redo] [:span.hidden.lg:inline "Redo"]]
       [:div.btn.btn-primary {:data-on:click (h/action
                                              (if (:dirty? @cursor)
                                                (swap! cursor assoc :modal {:type :quit})
                                                (shutdown! cursor *app-state)))}
        [:div.icon.icon-quit] [:span.hidden.lg:inline "Quit"]]]]]))

(def ^:private status->border-class
  {:ok "border-base-300"
   :new "border-warning"
   :error "border-error"})

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
     (dropdown/dropdown {:button-class (str "btn py-0 px-2 " (or (status->button-class descendant-status) "btn-neutral"))
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
                 "rounded-full text-sm border-2 bg-gray-500 border-base-100 text-white"
                 "flex items-center justify-center"
                 "w-8 h-8")}
        (count children)])]))

(defn- render-knot
  [cursor tree knot {:keys [debug-enabled? show-dynamic? fixed-width? active-knot-id]}]
  (let [{:keys [id label response unblessed status locked]} knot
        border-class (status->border-class status)
        active? (= id active-knot-id)]
    [:div.flex.flex-row
     {:id (str "knot-" id)}
     ;; Active-knot marker: sits in the left gutter, outside the knot content
     [:div.w-5.shrink-0.flex.items-start.justify-center.pt-2
      (when active?
        [:div.icon.icon-arrow-right {:title "Selected"}])]
     [:div.border-x-8.grow
      {:class (classes border-class (when active? "border-l-primary"))
       :data-on:click (h/action
                       (swap! cursor session/set-active-knot id)
                       (focus-if-leaf! cursor id))
       :style "cursor: pointer"}
      [:div.w-full.whitespace-pre-wrap.break-words.p-2.bg-base-100
       {:class (when (or fixed-width? (not= :ok status)) "font-mono")}
       [:div.whitespace-normal.font-sans.flex.flex-row.items-center.gap-x-2.float-right.sticky.top-24.rounded-bl-lg.pl-2.pb-1.bg-base-100
        (when locked
          [:div.icon.icon-lock {:title "Locked"}])
                               (when label
                                 [:span.font-bold.bg-neutral.text-neutral-content.p-1.rounded-md label])
        (render-children-navigation cursor tree knot)]
       (render-diff response unblessed)
       [:hr.clear-right.text-base-300]
       (when (and debug-enabled? show-dynamic?
                  (not= 0 id))
         (render-dynamic tree knot))]]]))



(defn- toolbar-btn
  "Renders a single operations-toolbar button.
  When a :data-tip is supplied the accel plugin will append the shortcut to it;
  when absent the accel plugin writes the shortcut as the full tooltip."
  [attrs icon]
  (let [has-tip? (contains? attrs :data-tip)
        attrs' (cond-> (into {} (remove (comp nil? val) attrs))
                 has-tip? (assoc :data-preserve-attr "data-tip"))]
    [:div.btn.btn-xs.btn-primary.tooltip.tooltip-bottom
     attrs'
     [:div.icon.w-4.h-4 {:class icon}]]))

(defn- render-operations-toolbar
  [cursor session]
  (let [{:keys [tree debug-enabled?]} session
        active-knot-id (:active-knot-id tree)
        knot (tree/get-knot tree active-knot-id)
        {:keys [id status dynamic-response]} knot
        root? (= 0 id)
        ok? (= :ok status)
        parent-id (:parent-id knot)
        child-id (get-in tree [:selected id])
        no-child? (nil? child-id)
        leaf-knot-id (:id (last (tree/selected-knots tree)))]
    [:div {:class (classes "bg-base-100 text-base-content border-base-200"
                           "px-2 sm:px-4 py-1"
                           "w-full border-b")}
     [:div.mx-auto.container.flex.items-center.gap-1
      ;; Navigation — left-aligned
      (toolbar-btn {:data-tip "First Knot"
                    :disabled root?
                    :data-on:click (when-not root?
                                     (h/action
                                      (swap! cursor session/set-active-knot 0)
                                      (scroll-knot-into-view! 0)))}
                   "icon-scroll-top")
      (toolbar-btn {:disabled root?
                    :data-tip "Parent knot"
                    :data-accel "ArrowUp"
                    :data-on:click (when-not root?
                                     (h/action
                                      (swap! cursor session/set-active-knot parent-id)
                                      (scroll-knot-into-view! parent-id)))}
                   "icon-arrow-up")
      (toolbar-btn {:disabled no-child?
                    :data-tip "Child knot"
                    :data-accel "ArrowDown"
                    :data-on:click (when-not no-child?
                                     (h/action
                                      (swap! cursor session/set-active-knot child-id)
                                      (scroll-knot-into-view! child-id)
                                      (focus-if-leaf! cursor child-id)))}
                   "icon-arrow-down")
      (toolbar-btn {:data-tip "Last Knot"
                    :disabled (= id leaf-knot-id)
                    :data-on:click (when-not (= id leaf-knot-id)
                                     (h/action
                                      (swap! cursor session/set-active-knot leaf-knot-id)
                                      (focus-if-leaf! cursor leaf-knot-id)))}
                   "icon-scroll-bottom")
      ;; Spacer pushes operations to the right
      [:div.grow]
      ;; Operations — right-aligned
      (toolbar-btn {:disabled ok?
                    :data-tip "Bless"

                    :data-on:click (h/action
                                    (swap! cursor session/bless id)
                                    (flash! "Blessed"))}
                   "icon-bless")
      (toolbar-btn {:disabled (or ok? root?)
                    :data-tip "Bless To Here"
                    :data-accel "b"
                    :data-on:click (when-not (or ok? root?)
                                     (h/action
                                      (swap! cursor session/bless-to id)
                                      (flash! "Blessed to here")))}
                   "icon-bless-to")
      (toolbar-btn {:data-tip "Replay"
                    :data-accel "y"
                    :data-on:click (str "@post('/action/replay-to/" id "')")}
                   "icon-play")
      (toolbar-btn {:data-tip "New Child"
                    :data-accel "a"
                    :data-on:click (h/action
                                    (swap! cursor session/prepare-new-child! id)
                                    (reset-and-focus-command-input!))}
                   "icon-add")
      ;; Modal-opening actions (focus goes to modal)
      (toolbar-btn {:disabled root?
                    :data-tip "Edit Command…"
                    :data-on:click (when-not root?
                                     (h/action
                                      (swap! cursor assoc :modal {:type :edit-command :knot-id id})))}
                   "icon-edit")
      (toolbar-btn {:disabled root?
                    :data-tip "Edit Label…"
                    :data-on:click (when-not root?
                                     (h/action
                                      (swap! cursor assoc :modal {:type :edit-label :knot-id id})))}
                   "icon-label")
      (toolbar-btn {:disabled root?
                    :data-tip "Toggle Lock"
                    :data-accel "k"
                    :data-on:click (when-not root?
                                     (h/action
                                      (swap! cursor session/toggle-lock id)
                                      (let [locked? (get-in @cursor [:tree :knots id :locked])]
                                        (flash! (if locked? "Locked" "Unlocked")))))}
                   "icon-lock")
      (toolbar-btn {:disabled root?
                    :data-tip "Insert Parent…"
                    :data-on:click (when-not root?
                                     (h/action
                                      (swap! cursor assoc :modal {:type :insert-parent :knot-id id})))}
                   "icon-insert")
      (toolbar-btn {:disabled root?
                    :data-tip "Delete"
                    :data-accel "d"
                    :data-on:click (when-not root?
                                     (h/action
                                      (let [[error session'] (session/delete! @cursor id)]
                                        (reset! cursor session')
                                        (flash! (if error {:message error :type :error} "Deleted")))))}
                   "icon-delete")
      (toolbar-btn {:disabled (or root? (nil? (:children knot)))
                    :data-tip "Splice Out"
                    :data-on:click (when-not (or root? (nil? (:children knot)))
                                     (h/action
                                      (let [[error session'] (session/splice-out! @cursor id)]
                                        (reset! cursor session')
                                        (flash! (if error {:message error :type :error} "Spliced Out")))))}
                   "icon-splice")
      ;; Debug-only operations (hidden when not debug-enabled?)
      (when debug-enabled?
        (list
         (toolbar-btn {:disabled (nil? dynamic-response)
                       :data-tip "Dynamic State…"
                       :data-on:click (h/action
                                       (swap! cursor assoc :modal {:type :dynamic-state :knot-id id}))}
                      "icon-dynamic")
         (toolbar-btn {:data-tip (if root? "Trace Startup…" "Trace…")
                       :data-on:click (h/action
                                       (swap! cursor session/check-for-changed-sources)
                                       (let [command (if root? "Startup"
                                                         (:command (tree/get-knot (:tree @cursor) id)))
                                             [trace-response session']
                                             (if root?
                                               (session/trace-startup! @cursor)
                                               (session/trace-command! @cursor id))]
                                         (reset! cursor session')
                                         (if trace-response
                                           (let [parsed (trace/parse-trace trace-response)
                                                 nodes (trace/build-tree parsed)]
                                             (swap! cursor assoc
                                                    :trace {:nodes nodes
                                                            :search ""
                                                            :node-count (trace/count-nodes nodes)
                                                            :command command}
                                                    :modal {:type :trace}))
                                           (swap! cursor common/maybe-apply-source-error))))}
                      "icon-trace")))]]))

(defn- render-fab
  [cursor session]
  (let [{:keys [debug-enabled? show-dynamic? fixed-width?]} session]
    [:div.fab
     [:div.btn.btn-lg.btn-circle.btn-primary
      {:tabindex "0"
       :role "button"}
      [:div.icon.icon-globe]]

     [:div.rounded-box.bg-base-100.border.border-base-200.flex.flex-col.items-start
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
  "Renders the appropriate modal based on the :modal key in the session,
  or the :progress key in the app-state."
  [cursor session *app-state]
  (let [{:keys [modal tree]} session
        progress (get-in @*app-state [:global :progress])]
    (cond
      progress
      (modals/progress *app-state progress)

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
          (modals/quit-modal cursor *app-state)

          :trace
          (modals/trace-modal cursor (:trace session))

          :source-error
          (modals/source-error cursor)

          nil)))))

(defn skein-page
  "Main hyper page function. Renders the full skein UI from the session cursor.
  Hyper calls this whenever the cursor changes and pushes the diff via SSE."
  [req]
  (let [cursor (h/global-cursor :session)
        *app-state (:hyper/app-state req)
        session @cursor
        {:keys [tree debug-enabled? show-dynamic? fixed-width? closing? replay-on-launch?]} session]

    (swap! cursor dissoc :replay-on-launch?)

    (if closing?
      ;; Server is shutting down — show close message
      [:div.flex.items-center.justify-center.h-screen
       [:div.text-center
        [:h2.text-2xl.font-semibold.text-base-content.mb-4 "Skein Shutdown"]
        [:p.text-base-content.opacity-70 "You may close this window now."]]]
      ;; Normal page render
      (let [flash (first (reset-vals! *pending-flash nil))
            active-knot-id (:active-knot-id tree)
            knots (tree/selected-knots tree)
            leaf-knot (last knots)]
        [:div.relative.px-8
         (when flash
           (flash/flash-message flash))
         ;; Single fixed header containing both toolbars — no gap possible between them
         [:div.fixed.top-0.start-0.w-full.z-30
          (navbar cursor session *app-state)
          (render-operations-toolbar cursor session)]
         ;; mt-24 clears the combined height of both fixed toolbars
         [:div.container.mx-lg.mx-auto.mt-24
          (map (fn [knot]
                 (render-knot cursor tree knot {:debug-enabled? debug-enabled?
                                                :show-dynamic? show-dynamic?
                                                :fixed-width? fixed-width?
                                                :active-knot-id active-knot-id}))
               knots)
          (new-command/new-command-input cursor (:id leaf-knot))]
         ;; Modal overlay
         (render-modal cursor session *app-state)
         ;; FAB for settings
         (render-fab cursor session)
         ;; On initial render, may want to trigger replay-all.
         (when replay-on-launch?
           [:div {:data-init "@post('/action/replay-all')"}])]))))
