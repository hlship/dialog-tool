(ns dialog-tool.skein.ui.modals
  (:require [dialog-tool.skein.ui.components.modal :as modal]))

(defn close-window
  []
  [modal/modal
   {:title   "Skein Shutdown"
    :buttons nil}
   [:div.text-large
    "You may close this window now."]])

(defn edit-command
  "Renders the edit command modal with optional error message."
  [id command error]
  [modal/modal
   (cond-> {:title "Edit Command"
            :signals {:editCommand command}}
     error (assoc :error error))
   [:form {:data-on:submit__stop (str "@post('/action/edit-command/" id "')")}
    [:div.mb-4
     [:label.block.text-sm.font-medium.text-gray-700.mb-2 {:for "edit-command-input"}
      "Command:"]
     [:input#edit-command-input
      {:type      "text"
       :data-bind "editCommand"
       :data-init "el.select()"
       :class     "w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"}]]]])

(defn insert-parent
  [id command error]
  [modal/modal
   (cond-> {:title "Insert Parent"
            :signals {:insertCommand command}}
     error (assoc :error error))
   [:form {:data-on:submit__stop (str "@post('/action/insert-parent/" id "')")}
    [:div.mb-4
     [:label.block.text-sm.font-medium.text-gray-700.mb-2 {:for "insert-parent-input"}
      "Command:"]
     [:input#insert-parent-input
      {:type      "text"
       :data-bind "insertCommand"
       :data-init "el.select()"
       :class     "w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"}]]]])

(defn edit-label
  "Renders the edit label modal with optional error message."
  [id label error]
  [modal/modal
   (cond-> {:title "Edit Label"
            :signals {:editLabel label}}
     error (assoc :error error))
   [:form {:data-on:submit__stop (str "@post('/action/edit-label/" id "')")}
    [:div.mb-4
     [:label.block.text-sm.font-medium.text-gray-700.mb-2 {:for "edit-label-input"}
      "Label:"]
     [:input#edit-label-input
      {:type      "text"
       :data-bind "editLabel"
       :data-init "el.select()"
       :class     "w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"}]]]])

(defn progress
  "Renders a progress modal for tracking operation progress.
   
   Options:
   - :current - Current item number (1-indexed)
   - :total - Total number of items
   - :label - Optional label for the current item
   - :operation - Name of the operation being performed"
  [{:keys [current total label operation]}]
  [modal/modal
   {:title   operation
    :buttons [modal/cancel-button {}]}
   [:div
    [:div {:class "flex justify-between mb-2"}
     [:span {:class "text-sm font-medium text-gray-700"}
      (str current "/" total)]
     (when label
       [:span {:class "text-sm text-gray-600"} label])]
    ;; Progress bar
    [:progress.progress.progress-primary.w-full
     {:value current :max total}]]])

(defn dynamic-state
  "Renders a modal dialog displaying the dynamic response"
  [dynamic-response]
  [modal/modal
   {:title   "Dynamic State"
    :buttons [modal/cancel-button {:label "OK"}]}
   [:div.whitespace-pre.text-sm.font-mono.overflow-y-auto.max-h-96 dynamic-response]])

(defn quit-modal
  "Renders a quit confirmation modal with options to cancel, save and quit, or quit without saving."
  []
  [modal/modal
   {:title   "Unsaved Changes"
    :buttons nil}
   [:div
    [:p.text-sm.text-gray-700.mb-4
     "You have unsaved changes. What would you like to do?"]
    [:div.flex.flex-col.gap-2
     [:button.btn.btn-primary
      {:type          "button"
       :data-on:click "@post('/action/save-and-quit')"}
      "Save and Quit"]
     [:button.btn.btn-warning
      {:type          "button"
       :data-on:click "@post('/action/quit-without-saving')"}
      "Quit Without Saving"]
     [modal/cancel-button {}]]]])
