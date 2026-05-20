(ns dialog-tool.skein.ui.modals
  (:require [clojure.string :as string]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.source-handlers :as source]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.ui.ansi :as ansi]
            [dialog-tool.skein.ui.common :as common]
            [dialog-tool.skein.ui.components.modal :as modal]
            [dialog-tool.skein.ui.trace-view :as trace-view]
            [hyper.core :as h]))

(defn source-error
  [cursor]
  (let [error    (get-in @cursor [:modal :error])
        location (source/parse-error-location error)]
    (modal/modal
      {:title   "Source Error"
       :cursor  cursor
       :buttons nil}
      [:div
       [:div.whitespace-pre.text-sm.overflow-y-auto.max-h-96
        {:data-init "el.focus()"}
        (ansi/ansi->hiccup error)]
       (when location
         (source/render-source-snippet (:file-path location) (:line location)))
       [:div.text-sm.mt-2
        "You should correct the error, then "
        [:b "Replay All"]
        "."]
       [:div.flex.justify-end.gap-2.mt-2
        (modal/cancel-button {:cursor cursor})
        [:button.btn.btn-primary
         {:data-on:click "@post('/action/replay-all')"}
         "Replay All"]]])))

(defn- do-edit-command
  [cursor id raw-command]
  (let [command (common/normalize-input raw-command)]
    (swap! cursor
           (fn [session]
             (let [[operation-error session'] (-> session
                                                  session/check-for-changed-sources
                                                  (session/edit-command! id command))
                   {:keys [error]} session']
               (cond
                 error
                 (common/setup-source-error session' error)

                 operation-error
                 (assoc-in session' [:modal :edit-command :error] operation-error)

                 :else                                      ;; Success!
                 (dissoc session' :modal)))))))

(defn edit-command
  "Renders the edit command modal."
  [cursor id command error]
  (modal/modal
   (cond-> {:title "Edit Command"
            :cursor cursor
            :buttons nil}
     error (assoc :error error))
   [:form {:data-on:submit__prevent
           (h/action
             (do-edit-command cursor id (get $form-data "command")))}
    [:div.mb-4
     [:label.block.text-sm.font-medium.text-base-content.mb-2 {:for "edit-command-input"}
      "Command:"]
     [:input#edit-command-input
      {:type "text"
       :name "command"
       :value (or command "")
       :data-init "el.select()"
       :class "w-full rounded-md border-base-300 shadow-sm focus:border-primary focus:ring-primary sm:text-sm p-2 border"}]]
    [:div.flex.justify-end.gap-2
     (modal/cancel-button {:cursor cursor})
     (modal/ok-button {})]]))

(defn insert-parent
  "Renders the insert parent modal."
  [cursor id command error]
  (modal/modal
   (cond-> {:title "Insert Parent"
            :cursor cursor
            :buttons nil}
     error (assoc :error error))
   [:form {:data-on:submit__prevent
           (h/action
            (let [cmd (some-> (get $form-data "command") common/normalize-input)]
              (swap! cursor session/check-for-changed-sources)
              (let [[operation-error session'] (session/insert-parent! @cursor id cmd)]
                (reset! cursor session')
                (cond
                  (:error session')    (swap! cursor common/maybe-apply-source-error)
                  operation-error      (swap! cursor assoc :modal {:type :insert-parent :knot-id id :error operation-error})
                  :else                (swap! cursor dissoc :modal)))))}
    [:div.mb-4
     [:label.block.text-sm.font-medium.text-base-content.mb-2 {:for "insert-parent-input"}
      "Command:"]
     [:input#insert-parent-input
      {:type "text"
       :name "command"
       :value (or command "")
       :data-init "el.select()"
       :class "w-full rounded-md border-base-300 shadow-sm focus:border-primary focus:ring-primary sm:text-sm p-2 border"}]]
    [:div.flex.justify-end.gap-2
     (modal/cancel-button {:cursor cursor})
     (modal/ok-button {})]]))

(defn edit-label
  "Renders the edit label modal."
  [cursor id label locked error]
  (modal/modal
   (cond-> {:title "Edit Label"
            :cursor cursor
            :buttons nil}
     error (assoc :error error))
   [:form {:data-on:submit__prevent
           (h/action
            (let [lbl (some-> (get $form-data "label") str string/trim)
                  locked? (= "on" (get $form-data "locked"))
                  t (:tree @cursor)
                  existing (when-not (string/blank? lbl)
                             (tree/find-by-label t lbl))
                  duplicate? (and existing (not= id (:id existing)))]
              (if duplicate?
                (swap! cursor assoc :modal
                       {:type :edit-label :knot-id id
                        :error (str "Label \"" lbl "\" is already used by another knot.")})
                (do
                  (swap! cursor session/label id lbl locked?)
                  (swap! cursor dissoc :modal)))))}
    [:div.mb-4
     [:label.block.text-sm.font-medium.text-base-content.mb-2 {:for "edit-label-input"}
      "Label:"]
     [:input#edit-label-input
      {:type "text"
       :name "label"
       :value (or label "")
       :data-init "el.select()"
       :class "w-full rounded-md border-base-300 shadow-sm focus:border-primary focus:ring-primary sm:text-sm p-2 border"}]]
    [:div.mb-4.flex.items-center.gap-2
     [:input#edit-locked-input
      {:type "checkbox"
       :name "locked"
       :checked locked
       :class "checkbox"}]
     [:label.text-sm.font-medium.text-base-content {:for "edit-locked-input"}
      "Locked (prevent deletion)"]]
    [:div.flex.justify-end.gap-2
     (modal/cancel-button {:cursor cursor})
     (modal/ok-button {})]]))

(defn progress
  "Renders a progress modal for tracking operation progress.
  app-state* is the hyper app-state atom (progress is stored at [:global :progress])."
  [*app-state {:keys [current total label operation]}]
  (modal/modal
   {:title operation
    :buttons (list
              [:button.btn.btn-neutral
               {:type "button"
                :data-on:click__stop (h/action
                                      (swap! *app-state assoc-in [:global :progress :continue] false))}
               "Cancel"])}
   [:div
    [:div {:class "flex justify-between mb-2"}
     [:span {:class "text-sm font-medium text-base-content"}
      (str current "/" total)]
     (when label
       [:span.text-sm.text-base-content.opacity-70 label])]
    ;; Progress bar
    [:progress.progress.progress-primary.w-full
     {:value current :max total}]]))

(defn dynamic-state
  "Renders a modal dialog displaying the dynamic response."
  [cursor dynamic-response]
  (let [[_ trimmed] (string/split dynamic-response #"\n" 2)]
    (modal/modal
     {:title "Dynamic State"
      :cursor cursor
      :buttons (list (modal/cancel-button {:cursor cursor :label "OK"}))}
     [:div.whitespace-pre.text-sm.font-mono.overflow-y-auto.max-h-96
      {:data-init "el.focus()"}
      (ansi/ansi->hiccup trimmed)])))

(defn quit-modal
  "Renders a quit confirmation modal."
  [cursor *app-state]
  (modal/modal
   {:title "Unsaved Changes"
    :cursor cursor
    :buttons nil}
   [:div
    [:p.text-sm.text-base-content.mb-4
     "You have unsaved changes. What would you like to do?"]
    [:div.flex.flex-col.gap-2
     [:button.btn.btn-primary
      {:type "button"
       :data-on:click (h/action
                       (swap! cursor session/save!)
                       (swap! cursor dissoc :modal)
                       ((requiring-resolve 'dialog-tool.skein.ui.app/shutdown!) cursor *app-state))}
      "Save and Quit"]
     [:button.btn.btn-warning
      {:type "button"
       :data-on:click (h/action
                       (swap! cursor dissoc :modal)
                       ((requiring-resolve 'dialog-tool.skein.ui.app/shutdown!) cursor *app-state))}
      "Quit Without Saving"]
     (modal/cancel-button {:cursor cursor})]]))

(defn trace-modal
  "Renders a modal displaying the trace tree for a command."
  [cursor trace-state]
  (modal/modal
   {:title (str "Trace: " (:command trace-state))
    :cursor cursor
    :buttons nil}
   [:div.flex.flex-col {:class "w-[85vw] h-[80vh]"}
    [:div.flex-1.min-h-0.flex.flex-col
     (trace-view/render-trace-tree cursor trace-state)]
    [:div.flex.justify-end.pt-4.flex-shrink-0
     (modal/cancel-button {:cursor cursor :label "Close"})]
    ;; Hidden popup for source preview on hover (positioned by JS)
    [:div#source-preview-popup.hidden.fixed.z-50.bg-white.text-black.border.border-gray-200.rounded-lg.shadow-xl.overflow-hidden
     {:class "max-w-[80vw]"}]]))
