(ns dialog-tool.skein.ui.modals
  (:require [clojure.string :as string]
            [dialog-tool.env :as env]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.source-handlers :as source]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.ui.actions :as actions]
            [dialog-tool.skein.ui.ansi :as ansi]
            [dialog-tool.skein.ui.common :as common]
            [dialog-tool.skein.ui.components.modal :as modal]
            [dialog-tool.skein.ui.trace-view :as trace-view]
            [hyper.core :as h]))

(defn dismiss-modal
  [*modal]
  (reset! *modal nil))

(defn source-error
  [*session]
  (let [*modal   (h/global-cursor :modal)
        {:keys [error]} @*modal
        location (source/parse-error-location error)]
    (modal/modal
      {:title "Source Error"}
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
        (modal/cancel-button {:cursor *session})
        [:button.btn.btn-primary
         {:data-on:click (h/action {:as "source-error-replay-all"}
                                   (actions/replay-all))}
         "Replay All"]]])))


(defn- handle-operation-error
  [[operation-error session]]
  (let [*session (h/global-cursor :session)
        *modal   (h/global-cursor :modal)
        {:keys [error]} session]
    (cond
      error
      (reset! *session (common/setup-source-error session error))

      operation-error
      (swap! *modal assoc :error operation-error)

      :else                                                 ;; Success!
      (do
        (reset! *session session)
        (dismiss-modal *modal)))))

(defn- do-edit-command
  [id raw-command]
  (env/log-action "edit-command:submit" id)
  (let [*session (h/global-cursor :session)
        command  (common/normalize-input raw-command)]
    (-> @*session
        session/check-for-changed-sources
        (session/edit-command! id command)
        handle-operation-error)))

(defn edit-command
  "Renders the edit command modal."
  []
  (let [*modal (h/global-cursor :modal)
        {:keys [id command error]} @*modal]
    (modal/modal
      (cond-> {:title "Edit Command"}
        error (assoc :error error))
      [:form {:data-on:submit__prevent
              (h/action {:as "edit-command:submit"}
                        (do-edit-command id (get $form-data "command")))}
       [:div.mb-4
        [:label.block.text-sm.font-medium.text-base-content.mb-2 {:for "edit-command-input"}
         "Command:"]
        [:input#edit-command-input
         {:type      "text"
          :name      "command"
          :value     (or command "")
          :data-init "el.select()"
          :class     "w-full rounded-md border-base-300 shadow-sm focus:border-primary focus:ring-primary sm:text-sm p-2 border"}]]
       [:div.flex.justify-end.gap-2
        (modal/cancel-button nil)
        (modal/ok-button {})]])))

(defn- do-insert-parent
  [id raw-command]
  (env/log-action "insert-parent:submit" id)
  (let [*session (h/global-cursor :session)
        command  (common/normalize-input raw-command)]
    (-> @*session
        session/check-for-changed-sources
        (session/insert-parent! id command)
        handle-operation-error)))

(defn insert-parent
  "Renders the insert parent modal."
  []
  (let [*modal (h/global-cursor :modal)
        {:keys [id command error]} @*modal]
    (modal/modal
      (cond-> {:title "Insert Parent"}
        error (assoc :error error))
      [:form {:data-on:submit__prevent
              (h/action {:as "insert-parent:submit"}
                        (do-insert-parent id (get $form-data "command")))}
       [:div.mb-4
        [:label.block.text-sm.font-medium.text-base-content.mb-2 {:for "insert-parent-input"}
         "Command:"]
        [:input#insert-parent-input
         {:type      "text"
          :name      "command"
          :value     (or command "")
          :data-init "el.select()"
          :class     "w-full rounded-md border-base-300 shadow-sm focus:border-primary focus:ring-primary sm:text-sm p-2 border"}]]
       [:div.flex.justify-end.gap-2
        (modal/cancel-button nil)
        (modal/ok-button {})]])))

(defn edit-label
  "Renders the edit label modal."
  [*session]
  (let [*modal (h/global-cursor :modal)
        {:keys [id label locked error]} @*modal]
    (modal/modal
      (cond-> {:title   "Edit Label"
               :buttons nil}
        error (assoc :error error))
      [:form {:data-on:submit__prevent
              (h/action {:as "edit-label:submit"}
                        (let [lbl        (some-> (get $form-data "label") str string/trim)
                              locked?    (= "on" (get $form-data "locked"))
                              t          (:tree @*session)
                              existing   (when-not (string/blank? lbl)
                                           (tree/find-by-label t lbl))
                              duplicate? (and existing (not= id (:id existing)))]
                          (if duplicate?
                            (swap! *modal assoc :error
                                   (str "Label \"" lbl "\" is already used by another knot."))
                            (do
                              (swap! *session session/label id lbl locked?)
                              (dismiss-modal *modal)))))}
       [:div.mb-4
        [:label.block.text-sm.font-medium.text-base-content.mb-2 {:for "edit-label-input"}
         "Label:"]
        [:input#edit-label-input
         {:type      "text"
          :name      "label"
          :value     (or label "")
          :data-init "el.select()"
          :class     "w-full rounded-md border-base-300 shadow-sm focus:border-primary focus:ring-primary sm:text-sm p-2 border"}]]
       [:div.mb-4.flex.items-center.gap-2
        [:input#edit-locked-input
         {:type    "checkbox"
          :name    "locked"
          :checked locked
          :class   "checkbox"}]
        [:label.text-sm.font-medium.text-base-content {:for "edit-locked-input"}
         "Locked (prevent deletion)"]]
       [:div.flex.justify-end.gap-2
        (modal/cancel-button {:cursor *session})
        (modal/ok-button {})]])))

(defn progress
  "Renders a progress modal for tracking operation progress.
  app-state* is the hyper app-state atom (progress is stored at [:global :progress])."
  []
  (let [*modal (h/global-cursor :modal)
        {:keys [current total label operation]} @*modal
        cancel #(swap! *modal assoc :continue false)]
    (when current
      (modal/modal
        {:title   operation
         :cancel  cancel
         :buttons (list
                    [:button.btn.btn-primary
                     {:type                "button"
                      :data-on:click__stop (h/action {:as "progress:cancel"}
                                                     (cancel))}
                     "Cancel"])}
        [:div
         [:div {:class "flex justify-between mb-2"}
          [:span {:class "text-sm font-medium text-base-content"}
           (str current "/" total)]
          (when label
            [:span.text-sm.text-base-content.opacity-70 label])]
         ;; Progress bar
         [:progress.progress.progress-primary.w-full
          {:value current :max total}]]))))

(defn dynamic-state
  "Renders a modal dialog displaying the dynamic response."
  []
  (let [*modal (h/global-cursor :modal)
        {:keys [dynamic-response]} @*modal
        [_ trimmed] (string/split dynamic-response #"\n" 2)]
    (modal/modal
      {:title   "Dynamic State"
       :buttons (list (modal/ok-button {:label  "OK"
                                        :submit #(dismiss-modal *modal)}))}
      [:div.whitespace-pre.text-sm.font-mono.overflow-y-auto.max-h-96
       {:data-init "el.focus()"}
       (ansi/ansi->hiccup trimmed)])))

(defn quit-modal
  "Renders a quit confirmation modal."
  ;; TODO: Make use of :hyper/env 
  [*session *app-state]
  (let [*modal (h/global-cursor :modal)
        {:keys [shutdown-fn]} @*app-state]
    (modal/modal
      {:title   "Unsaved Changes"}
      [:div
       [:p.text-sm.text-base-content.mb-4
        "You have unsaved changes. What would you like to do?"]
       [:div.flex.flex-col.gap-2
        [:button.btn.btn-primary
         {:type          "button"
          :data-on:click (h/action
                           (swap! *session session/save!)
                           (shutdown-fn))}
         "Save and Quit"]
        [:button.btn.btn-warning
         {:type          "button"
          :data-on:click (h/action
                           (shutdown-fn))}
         "Quit Without Saving"]
        (modal/cancel-button {:cancel #(dismiss-modal *modal)})]])))

(defn trace-modal
  "Renders a modal displaying the trace tree for a command."
  [*session]
  (let [*modal (h/global-cursor :modal)
        {:keys [trace-state]} @*modal]
    (modal/modal
      {:title   (str "Trace: " (:command trace-state))}
      [:div.flex.flex-col {:class "w-[85vw] h-[80vh]"}
       [:div.flex-1.min-h-0.flex.flex-col
        (trace-view/render-trace-tree *session trace-state)]
       [:div.flex.justify-end.pt-4.flex-shrink-0
        (modal/cancel-button {:label "Close"})]
       ;; Hidden popup for source preview on hover (positioned by JS)
       [:div#source-preview-popup.hidden.fixed.z-50.bg-white.text-black.border.border-gray-200.rounded-lg.shadow-xl.overflow-hidden
        {:class "max-w-[80vw]"}]])))

(defn render-modal
  "Renders the appropriate modal based on the global :modal cursor."
  [*session *app-state]
  (let [*modal (h/global-cursor :modal)
        {:keys [type]} @*modal]
    ;; h/reactive seems to do a wierd thing if the body returns nil
    (when type
      (h/reactive [*modal]
        (case type
          :progress
          (progress)

          :edit-command
          (edit-command)

          :edit-label
          (edit-label *session)

          :insert-parent
          (insert-parent)

          :dynamic-state
          (dynamic-state)

          :quit
          (quit-modal *session *app-state)

          :trace
          (trace-modal *session)

          :source-error
          (source-error *session))))))
