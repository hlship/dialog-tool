(ns dialog-tool.skein.ui.app
  (:require [clojure.string :as string]
            [dialog-tool.env :as env]
            [dialog-tool.skein.dynamic :as dynamic]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.ui.ansi :as ansi]
            [dialog-tool.skein.ui.components.dropdown :as dropdown]
            [dialog-tool.skein.ui.components.new-command :as new-command]
            [dialog-tool.skein.ui.tree-pane :as tree-pane]
            [dialog-tool.skein.ui.diff :as diff]
            [dialog-tool.skein.ui.modals :as modals]
            [dialog-tool.skein.ui.utils :refer [classes]]
            [dialog-tool.skein.ui.actions :as actions]
            [dialog-tool.skein.ui.common :as common :refer [session-cursor]]
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

(defn- render-search
  "Renders the knot search input and results dropdown in the operations toolbar."
  []
  (let [search-signal (h/local-signal :search-query "")
        *search (common/search-cursor)]
    [:div.relative.grow.focus-within:z-20
     [:label.input.input-sm.input-bordered.flex.items-center.gap-2.w-full.tooltip.tooltip-bottom.search-expand-label
      {:data-accel "f"
       :data-label "Search"}
      [:div.icon.w-4.h-4.icon-search {:aria-hidden "true"}]
      [:input#search-input.grow
       {:type "text"
        :placeholder "Search knots…"
        :autocomplete "off"
        :data-bind (:name search-signal)
        :data-on:input
        (h/action {:as "knot-search-update"}
                  (actions/search $value))
        :data-on:keydown
        (h/action {:as "knot-search-keydown"}
                  (case $key
                    "Escape" (actions/dismiss-search)
                    "ArrowDown" (effects/execute-script!
                                 "document.querySelector('#search-results button')?.focus()")
                    nil))}]]
     (when-let [{:keys [results]} @*search]
       (when (seq results)
         [:ul#search-results.absolute.z-50.menu.flex-col.bg-base-100.rounded-box.shadow-xl.p-2.overflow-y-auto.mt-1.flex-nowrap
          {:class "max-h-[30rem] w-[36rem]"
           :style "top: 100%; left: 0;"}
          (for [{:keys [knot-id snippet]} results]
            [:li
             [:button.w-full.text-left
              {:type "button"
               :data-on:keydown "sk.navigateSearchResults(evt, el)"
               :data-on:click
               (h/action {:as "search-knot-selected"}
                         (actions/jump-to-search-selection knot-id))}
              [:div.text-xs.whitespace-pre-line.line-clamp-7
               snippet]]])]))]))

(defn- navbar-btn
  "Renders a navbar button with a visible text label and a hover tooltip.
  attrs may include data-accel*, data-on:click, :disabled, and :class (for extra
  classes merged into the base btn classes). The accel plugin reads :data-label
  to build the tooltip-content child span (label + shortcut)."
  [attrs icon label]
  (let [extra-class (:class attrs)
        attrs' (into {} (remove (comp nil? val) (dissoc attrs :class)))]
    [:button (assoc attrs'
                    :type "button"
                    :class (classes "btn btn-primary tooltip tooltip-bottom" extra-class)
                    :data-label label
                    :data-preserve-attr "data-tip")
     [:div.icon {:class icon :aria-hidden "true"}]
     [:span.hidden.lg:inline label]]))

(defn- simple-action
  [*session label flash-message f]
  (env/log-action label)
  (swap! *session #(-> %
                       f
                       js/navigate-to-active-knot!))
  (actions/flash! flash-message))

(defn navbar
  [*session]
  (let [session @*session
        {:keys [skein-path tree dirty?]} session
        can-undo? (-> session :undo-stack not-empty)
        can-redo? (-> session :redo-stack not-empty)
        can-reload? (session/can-reload? session)
        {:keys [ok new error]} (tree/totals tree)
        labeled-knots (tree/labeled-knots-sorted tree)]
    [:nav {:class (classes "bg-base-100 text-base-content border-base-200 divide-base-200"
                           "px-2 sm:px-4 py-2.5"
                           "w-full border-b")}
     [:div.w-full.flex.items-center.gap-2
      [:div.self-center.truncate.text-xl.font-semibold.shrink.min-w-0
       skein-path]
      [:div.join.shrink-0.mx-auto
       [:div.bg-success.text-success-content.p-2.font-semibold.rounded-l-lg
        {:aria-label (str ok " ok knots")}
        ok]
       (if (pos? new)
         [:div.bg-warning.text-warning-content.p-2.font-semibold.cursor-pointer
          {:role          "button"
           :tabindex      "0"
           :aria-label    (str new " new knots")
           :data-on:click (h/action {:as "seek-new"}
                                    (actions/seek-status :new))}
          new]
         [:div.bg-warning.text-warning-content.p-2.font-semibold
          {:aria-label (str new " new knots")}
          new])
       (if (pos? error)
         [:div.bg-error.text-error-content.p-2.font-semibold.rounded-r-lg.cursor-pointer
          {:role          "button"
           :tabindex      "0"
           :aria-label    (str error " error knots")
           :data-on:click (h/action {:as "seek-error"}
                                    (actions/seek-status :error))}
          error]
         [:div.bg-error.text-error-content.p-2.font-semibold.rounded-r-lg
          {:aria-label (str error " error knots")}
          error])]
      [:div.flex.items-center.gap-1.shrink-0.ml-auto
              (dropdown/dropdown {:disabled              (<= (count labeled-knots) 1)
                                  :label                 (list [:div.icon.icon-jump] [:span.hidden.lg:inline "Jump"])
                                  :button-class          "btn-primary tooltip tooltip-bottom"
                                  :data-label            "Jump"
                                  :data-accel__alt       "j"
                                  :data-preserve-attr    "data-tip"}
                                 (for [{:keys [id label]} labeled-knots]
                                   (dropdown/button {:data-on:click (h/action {:as "jump-to-label"}
                                                                              (actions/jump-to-label id))}
                                                    label)))
       (navbar-btn {:data-on:click (h/action {:as "replay-all"}
                                             (actions/replay-all))
                    :data-accel__alt__shift "r"}
                   "icon-play" "Replay All")
       (navbar-btn {:data-on:click (h/action {:as "save"}
                                             (simple-action *session "save" "Saved" session/save!))
                    :data-accel "s"
                    :class (when dirty? "btn-soft")}
                   "icon-save" "Save")
       (navbar-btn {:data-on:click (h/action {:as "undo"}
                                             (simple-action *session "undo" "Undo" session/undo))
                    :data-accel "z"
                    :disabled (not can-undo?)}
                   "icon-undo" "Undo")
       (navbar-btn {:data-on:click (h/action {:as "redo"}
                                             (simple-action *session "redo" "Redo" session/redo))
                    :data-accel__shift "z"
                    :disabled (not can-redo?)}
                   "icon-redo" "Redo")
       (navbar-btn {:data-on:click (h/action {:as "reload"}
                                             (actions/reload))
                    :data-tip "Reload"
                    :disabled (not can-reload?)}
                   "icon-reload" "Reload")
       [:button.btn.btn-primary {:type "button"
                                  :data-on:click (h/action {:as "quit"}
                                                           (actions/quit))}
        [:div.icon.icon-quit {:aria-hidden "true"}] [:span.hidden.lg:inline "Quit"]]]]]))

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

(defn- render-knot
  [*session tree knot {:keys [debug-enabled? show-dynamic? fixed-width? active-knot-id]}]
  (let [{:keys [id response unblessed status locked label]} knot
        active? (= id active-knot-id)]
    [:div.flex.flex-row
     {:id (str "knot-" id)
      :data-knot-id (str id)}
     ;; Active-knot marker + status icon: sit in the left gutter, outside the knot content
     [:div.w-5.shrink-0.flex.flex-col.items-center.justify-start.pt-2.gap-1.pr-1
      (when active?
        [:div.icon.icon-arrow-right {:role "img" :aria-label "Selected"}])
      (case status
        :new   [:div.icon.icon-warning.w-4.h-4 {:role "img" :aria-label "New knot"}]
        :error [:div.icon.icon-error.w-4.h-4   {:role "img" :aria-label "Error knot"}]
        nil)]
     [:div
      {:class (classes "border-x-8 grow cursor-pointer"
                       (when active? "border-l-primary")
                       (status->border-class status))
       :data-on:click (h/action {:as "set-active-knot"}
                                (swap! *session #(-> %
                                                     (session/set-active-knot-id id)
                                                     js/navigate-to-active-knot!)))}
      [:div.w-full.whitespace-pre-wrap.break-words.p-2.bg-base-100
       {:class (when (or fixed-width? (not= :ok status)) "font-mono")}
       ;; Lock icon and label float to the right inside the knot content
       (when (or locked label)
         [:div.float-right.flex.flex-row.items-center.gap-1.pl-2.pb-1
          (when locked
            [:div.icon.icon-lock {:role "img" :aria-label "Locked"}])
          (when label
            [:span.font-bold.bg-neutral.text-neutral-content.px-1.py-0.5.rounded.text-sm
             label])])
       (render-diff response unblessed)
       (when (and debug-enabled?
                  show-dynamic?
                  (pos? id))
         (render-dynamic tree knot))]]]))

(defn- toolbar-btn
  "Renders a single operations-toolbar button.
  Pass :data-label — the accel plugin reads it to build data-tip (label + shortcut).
  data-preserve-attr ensures Datastar's morph never removes the plugin-written data-tip.
  Accepts an optional :tooltip-dir key (default \"bottom\") to control placement."
  [attrs icon]
  (let [tooltip-dir (get attrs :tooltip-dir "bottom")
        attrs' (into {} (remove (comp nil? val) (dissoc attrs :tooltip-dir)))]
    [:button (assoc attrs'
                    :type "button"
                    :class (classes "btn btn-xs btn-primary tooltip" (str "tooltip-" tooltip-dir))
                    :data-preserve-attr "data-tip")
     [:div.icon.w-4.h-4 {:class icon :aria-hidden "true"}]]))

(defn- render-operations-toolbar
  [*session selected-knots]
  (let [session @*session
        {:keys [tree debug-enabled?]} session
        active-knot-id (:active-knot-id tree)
        {:keys [id dynamic-response parent-id selected-child-id children unblessed]} (session/get-knot session active-knot-id)
        root?            (= 0 id)
        all-ok?          (->> selected-knots
                              (map :status)
                              (every? #(= % :ok)))
        leaf-knot-id     (-> selected-knots last :id)
        ;; Siblings: other children of parent, sorted alphabetically (matching nav graph order)
        sorted-siblings  (when-not root?
                           (->> (tree/find-children tree parent-id)
                                (sort-by :command)
                                vec))
        sibling-idx      (when (seq sorted-siblings)
                           (first (keep-indexed (fn [i s] (when (= (:id s) id) i))
                                                sorted-siblings)))
        prev-sibling-id  (when (and sibling-idx (pos? sibling-idx))
                           (:id (nth sorted-siblings (dec sibling-idx))))
        next-sibling-id  (when (and sibling-idx (< sibling-idx (dec (count sorted-siblings))))
                           (:id (nth sorted-siblings (inc sibling-idx))))]
    [:div {:class (classes "bg-base-100 text-base-content border-base-200"
                           "px-2 sm:px-4 py-1"
                           "w-full border-b")}
     [:div.w-full.flex.items-center.gap-1
      ;; Navigation — left-aligned
      (toolbar-btn {:data-label "First Knot"
                    :disabled root?
                    :tooltip-dir "right"
                    :data-accel__alt__shift "ArrowUp"
                    :data-on:click (when-not root?
                                     (h/action {:as "activate-first-knot"}
                                               (actions/activate-knot 0)))}
                   "icon-scroll-top")
      (toolbar-btn {:disabled root?
                    :data-label "Parent knot"
                    :data-accel__alt "ArrowUp"
                    :data-on:click (when-not root?
                                     (h/action {:as "activate-parent"}
                                               (actions/activate-knot parent-id)))}
                   "icon-arrow-up")
      (toolbar-btn {:disabled (nil? prev-sibling-id)
                    :data-label "Previous Sibling"
                    :data-accel__alt "ArrowLeft"
                    :data-on:click (when prev-sibling-id
                                     (h/action {:as "activate-prev-sibling"}
                                               (actions/select-tree-node prev-sibling-id)))}
                   "icon-arrow-left")
      (toolbar-btn {:disabled (nil? next-sibling-id)
                    :data-label "Next Sibling"
                    :data-accel__alt "ArrowRight"
                    :data-on:click (when next-sibling-id
                                     (h/action {:as "activate-next-sibling"}
                                               (actions/select-tree-node next-sibling-id)))}
                   "icon-arrow-right")
      (let [disabled? (not (seq (get-in tree [:children id])))]
        (toolbar-btn {:disabled disabled?
                      :data-label "Toggle Expand"
                      :data-accel__alt "x"
                      :data-on:click (when-not disabled?
                                       (h/action {:as "toggle-expand"}
                                                 (actions/toggle-tree-node id)))}
                     "icon-arrow-down-circle"))
      (let [disabled? (nil? selected-child-id)]
        (toolbar-btn {:disabled disabled?
                      :data-label "Child knot"
                      :data-accel__alt "ArrowDown"
                      :data-on:click (when-not disabled?
                                       (h/action {:as "activate-child"}
                                                 (actions/activate-knot selected-child-id)))}
                     "icon-arrow-down"))
      (let [disabled? (= id leaf-knot-id)]
        (toolbar-btn {:data-label "Last Knot"
                      :disabled disabled?
                      :data-accel__alt__shift "ArrowDown"
                      :data-on:click (when-not disabled?
                                       (h/action {:as "activate-last"}
                                                 (actions/activate-knot leaf-knot-id)))}
                     "icon-scroll-bottom"))
      ;; Search — fills the space between navigation and operations
      [:div.grow.flex.px-2
       (render-search)]
      ;; Operations — right-aligned
      (toolbar-btn {:disabled (not unblessed)
                    :data-label "Bless Knot"
                    :data-accel__alt "b"
                    :data-on:click (h/action {:as "bless-knot"}
                                             (actions/bless-knot id))}
                   "icon-bless")
      (toolbar-btn {:disabled all-ok?
                    :data-label "Bless Changes"
                    :data-accel__alt__shift "b"
                    :data-on:click (h/action {:as "bless"}
                                             (actions/bless-changes leaf-knot-id))}
                   "icon-bless-all")
      (toolbar-btn {:data-label "Replay"
                    :data-accel__alt "r"
                    :data-on:click (h/action {:as "replay-to"}
                                             (actions/replay-to id))}
                   "icon-play")
      (toolbar-btn {:data-label "New Child"
                    :data-accel__alt "a"
                    :data-on:click (h/action {:as "new-child"}
                                             (actions/new-child id))}
                   "icon-add")
      ;; Modal-opening actions (focus goes to modal)
      (toolbar-btn {:disabled root?
                    :data-label "Edit Command…"
                    :data-accel__alt "e"
                    :data-on:click (when-not root?
                                     (h/action {:as "edit-command"}
                                               (actions/edit-command id)))}
                   "icon-edit")
      (toolbar-btn {:disabled root?
                    :data-label "Edit Label…"
                    :data-accel__alt "l"
                    :data-on:click (when-not root?
                                     (h/action {:as "edit-label"}
                                               (actions/edit-label id)))}
                   "icon-label")
      (toolbar-btn {:disabled root?
                    :data-label "Toggle Lock"
                    :data-accel__alt "k"
                    :data-on:click (when-not root?
                                     (h/action {:as "toggle-lock"}
                                               (actions/toggle-lock id)))}
                   "icon-lock")
      (toolbar-btn {:disabled root?
                    :data-label      "Insert Parent…"
                    :data-accel__alt "i"
                    :data-on:click (when-not root?
                                     (h/action {:as "insert-parent"}
                                               (actions/insert-parent id)))}
                   "icon-insert")
      (toolbar-btn {:disabled root?
                    :data-label "Delete"
                    :data-accel__alt "d"
                    :data-on:click (when-not root?
                                     (h/action {:as "delete"}
                                               (actions/delete-knot id)))}
                   "icon-delete")
      (let [disabled? (or root? (nil? children))]
        (toolbar-btn {:disabled disabled?
                      :data-label "Splice Out"
                      :data-on:click (when-not disabled?
                                       (h/action {:as "splice-out"}
                                                 (actions/split-out id)))}
                     "icon-splice"))
      ;; Debug-only operations (hidden when not debug-enabled?)
      (when debug-enabled?
        (list
         (toolbar-btn {:disabled (nil? dynamic-response)
                       :data-label "Dynamic State…"
                       :data-accel__alt "s"
                       :data-on:click (h/action {:as "dynamic-state"}
                                                (actions/dynamic-state))}
                      "icon-dynamic")
         (toolbar-btn {:data-label (if root? "Trace Startup…" "Trace…")
                       :data-accel__alt "t"
                       :tooltip-dir "left"
                       :data-on:click (h/action {:as "trace"}
                                                (actions/trace))}
                      "icon-trace")))]]))

(defn- render-fab
  [*session]
  (let [{:keys [debug-enabled? show-dynamic? fixed-width?]} @*session]
    [:div.fab
     [:div.btn.btn-lg.btn-circle.btn-primary
      {:tabindex "0"
       :role "button"}
      [:div.icon.icon-globe]]

     [:div.rounded-box.bg-base-100.border.border-base-200.flex.flex-col.items-start
      [:label.label.p-2
       [:input.toggle {:type "checkbox"
                       :checked fixed-width?
                       :data-on:change (h/action {:as "toggle-fixed-width"}
                                                 (swap! *session update :fixed-width? not))}]
       "Fixed-width font"]
      [:label.label.p-2
       [:input.toggle {:type "checkbox"
                       :checked show-dynamic?
                       :disabled (not debug-enabled?)
                       :data-on:change (h/action {:as "toggle-show-dynamic"}
                                                 (swap! *session update :show-dynamic? not))}]
       "Show dynamic state"]]]))

(defn skein-page
  "Main hyper page function. Renders the full skein UI from the session cursor.
  Hyper calls this whenever the :session cursor changes and pushes the diff via SSE."
  [_req]
  (let [*session (session-cursor)
        session @*session
        {:keys [tree debug-enabled? show-dynamic? fixed-width? closing? replay-on-launch? loading?]} session]
    ;; This is done early to avoid a possible (?) race condition when the SSE stream
    ;; is initialized.
    (when replay-on-launch?
      (swap! *session dissoc :replay-on-launch?))

    (list
     (cond
       closing?
        ;; Server is shutting down — show close message
       [:div#skein-shutdown.flex.items-center.justify-center.h-screen
        [:div.text-center
         [:h2.text-2xl.font-semibold.text-base-content.mb-4 "Skein Shutdown"]
         [:p.text-base-content.opacity-70 "You may close this window now."]]]

       loading?
        ;; New skein: process hasn't started yet — show a placeholder until
        ;; replay-on-launch fires and replay-all! clears the :loading? flag.
       [:div.flex.items-center.justify-center.py-16
        {:data-init (h/action {:as "initial-load"}
                              (actions/initial-load))}
        [:span.loading.loading-spinner.loading-lg.text-primary]]

       :else
        ;; Normal page render
       (let [active-knot-id (session/get-active-knot-id session)
             knots (session/selected-knots session)
             leaf-knot (last knots)]
         [:div.relative
          (when replay-on-launch?
             ;; This lets the client render the initial page before we start sending down
             ;; SSE updates.
            [:div {:data-init
                   (h/action {:as "initial-replay"}
                             (actions/initial-load))}])
           ;; Single fixed header containing both toolbars — no gap possible between them
          [:div.fixed.top-0.start-0.w-full.z-30
           (navbar *session)
           (render-operations-toolbar *session knots)] ;; mt-28 clears the combined height of both fixed toolbars (with room for the badge)
          [:div.flex.flex-row.w-full.mt-28
           ;; Left: tree pane (sticky, resizable, scrolls independently).
           ;; data-preserve-attr keeps Datastar's morph from resetting the inline width
           ;; that the JS drag handler writes.
           [:div#tree-pane-outer
            {:class "sticky top-28 shrink-0 h-[calc(100vh-7rem)] flex flex-row"
             :style "width: 50rem"
             :data-preserve-attr "style"
             :data-init (h/action {:as "init-tree-pane-resize"}
                                  (effects/execute-script! "sk.initTreePaneResize()"))}
            ;; Tree pane — flex-1 fills available width.
            ;; bg-base-200 here (not on #tree-pane) so the background covers
            ;; the full scroll region, not just the content extent.
            [:div {:class "flex-1 min-w-0 bg-base-200 border-r border-base-300"}
             (tree-pane/render-tree-pane *session)]
            ;; Drag handle on the right edge
            [:div#tree-pane-handle
             {:class "w-1 shrink-0 cursor-col-resize bg-base-300 hover:bg-primary transition-colors"}]]
           ;; Right: transcript (takes remaining width, scrolls normally)
           [:div.flex-1.min-w-0.px-2
            (map (fn [knot]
                   (render-knot *session tree knot {:debug-enabled? debug-enabled?
                                                    :show-dynamic? show-dynamic?
                                                    :fixed-width? fixed-width?
                                                    :active-knot-id active-knot-id}))
                 knots)
            (new-command/new-command-input *session (:id leaf-knot))]]
           ;; Modal overlay
          (modals/render-modal)
           ;; FAB for settings
          (render-fab *session)])))))
