(ns dialog-tool.skein.ui.app
  (:require [clojure.string :as string]
            [dialog-tool.env :as env]
            [dialog-tool.skein.dynamic :as dynamic]
            [dialog-tool.skein.search :as search]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.ui.ansi :as ansi]
            [dialog-tool.skein.ui.components.dropdown :as dropdown]
            [dialog-tool.skein.ui.components.new-command :as new-command]
            [dialog-tool.skein.ui.diff :as diff]
            [dialog-tool.skein.ui.modals :as modals]
            [dialog-tool.skein.ui.utils :refer [classes]]
            [dialog-tool.skein.ui.actions :as actions]
            [dialog-tool.skein.ui.js :as js]
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
    (let [response  (ansi/ansi->markers response)
          unblessed (ansi/ansi->markers unblessed)
          changes   (diff/diff-text response unblessed)]
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
  [*session *app-state]
  ;; Set closing state so the page renders the close message
  (swap! *session assoc :closing? true)
  (future
    ;; Give hyper time to push the closing message to the browser
    (Thread/sleep 500)
    ((get-in @*app-state [:global :shutdown-fn]))))


(defn- jump-to-status!
  [*session status]
  (let [session  @*session
        tree     (:tree session)
        matching (tree/knots-with-status tree status)]
    (when (seq matching)
      (let [last-id  (get-in session [:last-jump status])
            idx      (when last-id
                       (let [i (.indexOf matching last-id)]
                         (when (>= i 0) i)))
            next-idx (if idx
                       (mod (inc idx) (count matching))
                       0)
            next-id  (nth matching next-idx)]
        (swap! *session #(-> %
                             (assoc-in [:last-jump status] next-id)
                             (session/select-knot next-id)
                             (session/set-active-knot next-id)))))))

(defn- swap-session!
  "Swaps the session in the app-state atom. Works from any thread."
  ([*app-state f]
   (swap! *app-state update-in [:global :session] f))
  ([*app-state f & args]
   (apply swap! *app-state update-in [:global :session] f args)))

(defn- dismiss-search!
  "Clears the search results from the session and resets the search input."
  [cursor]
  (swap! cursor dissoc :search)
  (effects/execute-script! "document.getElementById('search-input').value = ''"))

(defn- render-search
  "Renders the knot search input and results dropdown in the operations toolbar."
  [*session]
  (let [search-signal (h/local-signal :search-query "")]
    [:div.relative.grow.focus-within:z-20
     [:label.input.input-sm.input-bordered.flex.items-center.gap-2.w-full.tooltip.tooltip-bottom.search-expand-label
      {:data-accel         "f"
       :data-tip           "Search"
       :data-preserve-attr "data-tip"}
      [:div.icon.w-4.h-4.icon-search]
      [:input#search-input
       {:type         "text"
        :placeholder  "Search knots…"
        :autocomplete "off"
        :class        "grow"
        :data-bind    (:name search-signal)
        :data-on:input
        (h/action {:as "knot-search-update"}
                  (let [q       (string/trim (str $value))
                        session @*session]
                    (if (string/blank? q)
                      (swap! *session dissoc :search)
                      (swap! *session assoc :search
                             {:query   q
                              :results (search/search-knots (:tree session) q 50)}))))
        :data-on:keydown
        (h/action {:as "knot-search-keydown"}
                  (case $key
                    "Escape" (dismiss-search! *session)
                    "ArrowDown" (effects/execute-script!
                                  "document.querySelector('#search-results button')?.focus()")
                    nil))}]]
     (when-let [{:keys [results query]} (:search @*session)]
       (when (seq results)
         [:ul#search-results.absolute.z-50.menu.flex-col.bg-base-100.rounded-box.shadow-xl.p-2.overflow-y-auto.mt-1.flex-nowrap
          {:class "max-h-[30rem] w-[36rem]"
           :style "top: 100%; left: 0;"}
          (for [{:keys [knot-id snippet]} results]
            [:li
             [:button.w-full.text-left
              {:type            "button"
               :data-on:keydown "sk.navigateSearchResults(evt, el)"
               :data-on:click
               (h/action {:as "search-knot-selected"}
                         (dismiss-search! *session)
                         (swap! *session session/select-knot knot-id)
                         (swap! *session session/set-active-knot knot-id)
                         (js/scroll-knot-into-view! knot-id))}
              [:div.text-xs.whitespace-pre-line.line-clamp-7
               (search/highlight-snippet snippet query)]]])]))]))

(defn navbar
  [*session *app-state]
  (let [session       @*session
        {:keys [skein-path tree dirty?]} session
        can-undo?     (-> session :undo-stack not-empty)
        can-redo?     (-> session :redo-stack not-empty)
        can-reload?   (session/can-reload? session)
        {:keys [ok new error]} (tree/totals tree)
        labeled-knots (tree/labeled-knots-sorted tree)]
    [:nav {:class (classes "bg-base-100 text-base-content border-base-200 divide-base-200"
                           "px-2 sm:px-4 py-2.5"
                           "w-full border-b")}
     [:div.w-full.flex.items-center.gap-2
      [:div.self-center.truncate.text-xl.font-semibold.shrink.min-w-0
       skein-path]
      [:div.join.shrink-0.mx-auto
       [:div.bg-success.text-success-content.p-2.font-semibold.rounded-l-lg ok]
       [:div.bg-warning.text-warning-content.p-2.font-semibold
        (when (pos? new)
          {:class         "cursor-pointer"
           :data-on:click (h/action {:as "seek-new"}
                                    (jump-to-status! *session :new)
                                    (env/log-action "seek-new" (get-in @*session [:tree :active-knot-id]))
                                    (js/navigate-to-active-knot! *session))})
        new]
       [:div.bg-error.text-error-content.p-2.font-semibold.rounded-r-lg
        (when (pos? error)
          {:class         "cursor-pointer"
           :data-on:click (h/action {:as "seek-error"}
                                    (jump-to-status! *session :error)
                                    (env/log-action "seek-error" (get-in @*session [:tree :active-knot-id]))
                                    (js/navigate-to-active-knot! *session))})
        error]]
      [:div.flex.items-center.gap-1.shrink-0.ml-auto
       (dropdown/dropdown {:disabled (<= (count labeled-knots) 1)
                           :label    (list [:div.icon.icon-jump] [:span.hidden.lg:inline "Jump"])}
                          (for [{:keys [id label]} labeled-knots]
                            (dropdown/button {:data-on:click (h/action {:as "jump-to-label"}
                                                                       #(-> %
                                                                            (session/select-knot id)
                                                                            (session/set-active-knot id)
                                                                            (js/focus-if-leaf! id)))}
                                             label)))
       [:div.btn.btn-primary.tooltip.tooltip-bottom
        {:data-on:click          (h/action {:as "replay-all"}
                                           (actions/replay-all))
         :data-accel__alt__shift "r"
         :data-preserve-attr     "data-tip"}
        [:div.icon.icon-play] [:span.hidden.lg:inline "Replay All"]]
       [:div.btn.btn-primary.tooltip.tooltip-bottom
        {:data-on:click      (h/action {:as "save"}
                                       (env/log-action "save")
                                       (swap! *session session/save!)
                                       (actions/flash! "Saved"))
         :data-accel         "s"
         :data-preserve-attr "data-tip"
         :class              (when dirty? "btn-soft")}
        [:div.icon.icon-save] [:span.hidden.lg:inline "Save"]]
       [:div.btn.btn-primary.tooltip.tooltip-bottom
        {:data-on:click      (h/action {:as "undo"}
                                       (env/log-action "undo")
                                       (swap! *session session/undo)
                                       (actions/flash! "Undo")
                                       (js/navigate-to-active-knot! *session))
         :data-accel         "z"
         :data-preserve-attr "data-tip"
         :disabled           (not can-undo?)}
        [:div.icon.icon-undo] [:span.hidden.lg:inline "Undo"]]
       [:div.btn.btn-primary.tooltip.tooltip-bottom
        {:data-on:click      (h/action {:as "redo"}
                                       (env/log-action "redo")
                                       (swap! *session session/redo)
                                       (actions/flash! "Redo")
                                       (js/navigate-to-active-knot! *session))
         :data-accel__shift  "z"
         :data-preserve-attr "data-tip"
         :disabled           (not can-redo?)}
        [:div.icon.icon-redo] [:span.hidden.lg:inline "Redo"]]
       [:div.btn.btn-primary.tooltip.tooltip-bottom
        {:data-on:click      (h/action {:as "reload"}
                                       (swap! *session session/reload!)
                                       (actions/flash! "Reloaded")
                                       (js/navigate-to-active-knot! *session))
         :data-preserve-attr "data-tip"
         :data-tip           "Reload"
         :disabled           (not can-reload?)}
        [:div.icon.icon-reload] [:span.hidden.lg:inline "Reload"]]
       [:div.btn.btn-primary {:data-on:click (h/action {:as "quit"}
                                                       (if (:dirty? @*session)
                                                         (swap! *session assoc :modal {:type :quit})
                                                         (shutdown! *session *app-state)))}
        [:div.icon.icon-quit] [:span.hidden.lg:inline "Quit"]]]]]))

(def ^:private status->border-class
  {:ok    "border-base-300"
   :new   "border-warning"
   :error "border-error"})

(def ^:private status->button-class
  {:ok    nil
   :new   "bg-warning"
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
    (when (seq dynamic-state)
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
                         :disabled     (< (count children) 2)
                         :label        [:div.icon.icon-children]}
                        (map (fn [{:keys [id label command]}]
                               (let [status (tree/greatest-status (tree/knot-status tree id)
                                                                  (tree/descendant-status tree id))]
                                 (dropdown/button {:bg-class      (status->button-class status)
                                                   :data-on:click (h/action {:as "select-child"}
                                                                            (swap! cursor session/select-knot id))}
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
  [*session tree knot {:keys [debug-enabled? show-dynamic? fixed-width? active-knot-id]}]
  (let [{:keys [id label response unblessed status locked]} knot
        active? (= id active-knot-id)]
    [:div.flex.flex-row
     {:id (str "knot-" id)}
     ;; Active-knot marker: sits in the left gutter, outside the knot content
     [:div.w-5.shrink-0.flex.items-start.justify-center.pt-2
      (when active?
        [:div.icon.icon-arrow-right {:title "Selected"}])]
     [:div.border-x-8.grow.cursor-pointer
      {:class         (if active?
                        "border-l-primary"
                        (status->border-class status))
       :data-on:click (h/action {:as "set-active-knot"}
                                (swap! *session session/set-active-knot id)
                                (js/focus-if-leaf! *session id))}
      [:div.w-full.whitespace-pre-wrap.break-words.p-2.bg-base-100
       {:class (when (or fixed-width? (not= :ok status)) "font-mono")}
       [:div.whitespace-normal.font-sans.flex.flex-row.items-center.gap-x-2.float-right.sticky.top-28.rounded-bl-lg.pl-2.pb-1.bg-base-100
        (when locked
          [:div.icon.icon-lock {:title "Locked"}])
        (when label
          [:span.font-bold.bg-neutral.text-neutral-content.p-1.rounded-md label])
        (render-children-navigation *session tree knot)]
       (render-diff response unblessed)
       (when (and debug-enabled?                            ;; Running dgdebug? 
                  show-dynamic?
                  ;; Don't render dynamic for root knot, as that ends up being a a dump of every
                  ;; object's flags and variables.  We may do something about that in the future
                  ;; (just show globals for root knot, perhaps).
                  (pos? id))
         (render-dynamic tree knot))]]]))

(defn- toolbar-btn
  "Renders a single operations-toolbar button.
  When a :data-tip is supplied the accel plugin will append the shortcut to it;
  when absent the accel plugin writes the shortcut as the full tooltip.
  Accepts an optional :tooltip-dir key (default \"bottom\") to control placement."
  [attrs icon]
  (let [tooltip-dir (get attrs :tooltip-dir "bottom")
        ;; No data-preserve-attr: the server sends the current label on every morph.
        ;; The accel plugin watches data-tip with a MutationObserver and re-appends
        ;; the keyboard shortcut whenever the label changes.
        attrs'      (into {} (remove (comp nil? val) (dissoc attrs :tooltip-dir)))]
    [:div (assoc attrs' :class (classes "btn btn-xs btn-primary tooltip" (str "tooltip-" tooltip-dir)))

     [:div.icon.w-4.h-4 {:class icon}]]))

(defn- render-operations-toolbar
  [*session]
  (let [session        @*session
        *modal         (h/global-cursor :modal)
        {:keys [tree debug-enabled?]} session
        active-knot-id (:active-knot-id tree)
        knot           (tree/get-knot tree active-knot-id)
        {:keys [id status dynamic-response parent-id]} knot
        root?          (= 0 id)
        ok?            (= :ok status)
        child-id       (get-in tree [:selected id])
        no-child?      (nil? child-id)
        leaf-knot-id   (-> tree
                           tree/selected-knots
                           last
                           :id)]
    [:div {:class (classes "bg-base-100 text-base-content border-base-200"
                           "px-2 sm:px-4 py-1"
                           "w-full border-b")}
     [:div.w-full.flex.items-center.gap-1
      ;; Navigation — left-aligned
      (toolbar-btn {:data-tip               "First Knot"
                    :disabled               root?
                    :data-accel__alt__shift "ArrowUp"
                    :data-on:click          (when-not root?
                                              (h/action {:as "activate-first-knot"}
                                                        (swap! *session session/set-active-knot 0)
                                                        (js/scroll-knot-into-view! 0)))}
                   "icon-scroll-top")
      (toolbar-btn {:disabled        root?
                    :data-tip        "Parent knot"
                    :data-accel__alt "ArrowUp"
                    :data-on:click   (when-not root?
                                       (h/action {:as "activate-parent"}
                                                 (swap! *session session/set-active-knot parent-id)
                                                 (js/scroll-knot-into-view! parent-id)))}
                   "icon-arrow-up")
      (toolbar-btn {:disabled        no-child?
                    :data-tip        "Child knot"
                    :data-accel__alt "ArrowDown"
                    :data-on:click   (when-not no-child?
                                       (h/action {:as "activate-child"}
                                                 (swap! *session session/set-active-knot child-id)
                                                 (js/scroll-knot-into-view! child-id)
                                                 (js/focus-if-leaf! *session child-id)))}
                   "icon-arrow-down")
      (toolbar-btn {:data-tip               "Last Knot"
                    :disabled               (= id leaf-knot-id)
                    :data-accel__alt__shift "ArrowDown"
                    :data-on:click          (when-not (= id leaf-knot-id)
                                              (h/action {:as "activate-last"}
                                                        (swap! *session session/set-active-knot leaf-knot-id)
                                                        (js/focus-if-leaf! *session leaf-knot-id)))}
                   "icon-scroll-bottom")
      ;; Search — fills the space between navigation and operations
      [:div.grow.flex.px-2
       (render-search *session)]
      ;; Operations — right-aligned
      (toolbar-btn {:disabled      ok?
                    :data-tip      "Bless"
                    :data-on:click (h/action {:as "bless"}
                                             (actions/bless *session id))}
                   "icon-bless")
      (toolbar-btn {:disabled        (or ok? root?)
                    :data-tip        "Bless To Here"
                    :data-accel__alt "b"
                    :data-on:click   (when-not (or ok? root?)
                                       (h/action {:as "bless-to-here"}
                                                 (actions/bless-to-here *session id)))}
                   "icon-bless-to")
      (toolbar-btn {:data-tip        "Replay"
                    :data-accel__alt "r"
                    :data-on:click   (h/action {:as "replay-to"}
                                               (actions/replay-to id))}
                   "icon-play")
      (toolbar-btn {:data-tip        "New Child"
                    :data-accel__alt "a"
                    :data-on:click   (h/action {:as "new-child"}
                                               (actions/new-child *session id))}
                   "icon-add")
      ;; Modal-opening actions (focus goes to modal)
      (toolbar-btn {:disabled        root?
                    :data-tip        "Edit Command…"
                    :data-accel__alt "e"
                    :data-on:click   (when-not root?
                                       (h/action {:as "edit-command"}
                                                 (actions/edit-command *session id)))}
                   "icon-edit")
      (toolbar-btn {:disabled        root?
                    :data-tip        "Edit Label…"
                    :data-accel__alt "l"
                    :data-on:click   (when-not root?
                                       (h/action {:as "edit-label"}
                                                 (actions/edit-label *session id)))}
                   "icon-label")
      (toolbar-btn {:disabled        root?
                    :data-tip        "Toggle Lock"
                    :data-accel__alt "k"
                    :data-on:click   (when-not root?
                                       (h/action {:as "toggle-lock"}
                                                 (actions/toggle-lock *session id)))}
                   "icon-lock")
      (toolbar-btn {:disabled      root?
                    :data-tip      "Insert Parent…"
                    :data-on:click (when-not root?
                                     (h/action {:as "insert-parent"}
                                               (actions/insert-parent id)))}
                   "icon-insert")
      (toolbar-btn {:disabled        root?
                    :data-tip        "Delete"
                    :data-accel__alt "d"
                    :data-on:click   (when-not root?
                                       (h/action {:as "delete"}
                                                 (actions/delete-knot *session id)))}
                   "icon-delete")
      (toolbar-btn {:disabled      (or root? (nil? (:children knot)))
                    :data-tip      "Splice Out"
                    :data-on:click (when-not (or root? (nil? (:children knot)))
                                     (h/action {:as "splice-out"}
                                               (actions/split-out *session id)))}
                   "icon-splice")
      ;; Debug-only operations (hidden when not debug-enabled?)
      (when debug-enabled?
        (list
          (toolbar-btn {:disabled        (nil? dynamic-response)
                        :data-tip        "Dynamic State…"
                        :data-accel__alt "s"
                        :data-on:click   (h/action {:as "dynamic-state"}
                                                   (actions/dynamic-state))}
                       "icon-dynamic")
          (toolbar-btn {:data-tip        (if root? "Trace Startup…" "Trace…")
                        :data-accel__alt "t"
                        :tooltip-dir     "left"
                        :data-on:click   (h/action {:as "trace"}
                                                   (actions/trace))}
                       "icon-trace")))]]))

(defn- render-fab
  [*session]
  (let [{:keys [debug-enabled? show-dynamic? fixed-width?]} @*session]
    [:div.fab
     [:div.btn.btn-lg.btn-circle.btn-primary
      {:tabindex "0"
       :role     "button"}
      [:div.icon.icon-globe]]

     [:div.rounded-box.bg-base-100.border.border-base-200.flex.flex-col.items-start
      [:label.label.p-2
       [:input.toggle {:type           "checkbox"
                       :checked        fixed-width?
                       :data-on:change (h/action {:as "toggle-fixed-width"}
                                                 (swap! *session update :fixed-width? not))}]
       "Fixed-width font"]
      [:label.label.p-2
       [:input.toggle {:type           "checkbox"
                       :checked        show-dynamic?
                       :disabled       (not debug-enabled?)
                       :data-on:change (h/action {:as "toggle-show-dynamic"}
                                                 (swap! *session update :show-dynamic? not))}]
       "Show dynamic state"]]]))



(defn skein-page
  "Main hyper page function. Renders the full skein UI from the session cursor.
  Hyper calls this whenever the :session cursor changes and pushes the diff via SSE."
  [req]
  (let [*session   (h/global-cursor :session)
        *app-state (:hyper/app-state req)
        session    @*session
        {:keys [tree debug-enabled? show-dynamic? fixed-width? closing? replay-on-launch? loading?]} session]
 
    ;; Not sure this h/reactive is actually accomplishing anything, though I think the nested reactive for
    ;; *modal probably does.
    (h/reactive [*session]
      ;; This is done early to avoid a possible (?) race condition when the SSE stream
      ;; is initialized.
      (when replay-on-launch?
        (swap! *session dissoc :replay-on-launch?))

      (if closing?
        ;; Server is shutting down — show close message
        [:div.flex.items-center.justify-center.h-screen
         [:div.text-center
          [:h2.text-2xl.font-semibold.text-base-content.mb-4 "Skein Shutdown"]
          [:p.text-base-content.opacity-70 "You may close this window now."]]]
        ;; Normal page render
        (let [flash          (first (reset-vals! actions/*pending-flash nil))
              active-knot-id (:active-knot-id tree)
              knots          (tree/selected-knots tree)
              leaf-knot      (last knots)]
          [:div.relative
           ;; Flash trigger: a hidden span whose data-init fires sk.showFlash once on
           ;; insertion. Uses a random id so each flash is a new element to the morph
           ;; algorithm. Works in both action and cursor-change render contexts.
           (when flash
             (let [{:keys [message type]} (if (string? flash)
                                            {:message flash :type :info}
                                            flash)]
               ;; TODO: I don't think we need the rendered elements, the atom, or anything
               ;; beyond sk.showFlash().  Or maybe a placeholder with
               ;; data-ignore (or data-ignore-morph).
               [:span {:id        (str "flash-trigger-" (random-uuid))
                       :data-init (str "sk.showFlash(" (pr-str message) "," (pr-str (name type)) ")")
                       :style     "display:none"}]))
           ;; Single fixed header containing both toolbars — no gap possible between them
           [:div.fixed.top-0.start-0.w-full.z-30
            (navbar *session *app-state)
            (render-operations-toolbar *session)]
           ;; mt-28 clears the combined height of both fixed toolbars (with room for the badge)
           [:div.w-full.mt-28.px-2
            (if loading?
              ;; New skein: process hasn't started yet — show a placeholder until
              ;; replay-on-launch fires and replay-all! clears the :loading? flag.
              [:div.flex.items-center.justify-center.py-16
               [:span.loading.loading-spinner.loading-lg.text-primary]]
              ;; This is the main part of the page: the root knot and
              ;; each selected child until we hit a leaf, followed by
              ;; a command input field.
              (list
                (map (fn [knot]
                       (render-knot *session tree knot {:debug-enabled? debug-enabled?
                                                        :show-dynamic?  show-dynamic?
                                                        :fixed-width?   fixed-width?
                                                        :active-knot-id active-knot-id}))
                     knots)
                (new-command/new-command-input *session (:id leaf-knot))))]
           ;; Modal overlay
           (modals/render-modal *session *app-state)
           ;; FAB for settings
           (render-fab *session)
           ;; On initial render, may want to trigger replay-all.
           (when replay-on-launch?
             ;; This lets the client render the initial page before we start sending down
             ;; SSE updates.
             [:div {:data-init (h/action {:as "initial-replay-all"}
                                         (actions/replay-all))}])])))))
