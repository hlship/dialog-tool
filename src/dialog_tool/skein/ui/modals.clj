(ns dialog-tool.skein.ui.modals
  (:require [dialog-tool.skein.ui.components.modal :as modal]))

(defn edit-command
  "Renders the edit command modal with optional error message."
  [id command error]
  [modal/modal
   (cond-> {:title "Edit Command"
            :signals {:editCommand command}
            :content
            [:form {:data-on:submit__stop (str "@post('/action/edit-command/" id "')")}
             [:div.mb-4
              [:label.block.text-sm.font-medium.text-gray-700.mb-2 {:for "edit-command-input"}
               "Command:"]
              [:input#edit-command-input
               {:type "text"
                :data-bind "editCommand"
                :data-init "el.select()"
                :class "w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"}]]
             [:div.flex.justify-end.gap-2
              [modal/cancel-button {}]
              [modal/ok-button {}]]]}
     error (assoc :error error))])

(defn insert-parent
  [id command error]
  [modal/modal
   (cond-> {:title "Insert Parent"
            :signals {:insertCommand command}
            :content
            [:form {:data-on:submit__stop (str "@post('/action/insert-parent/" id "')")}
             [:div.mb-4
              [:label.block.text-sm.font-medium.text-gray-700.mb-2 {:for "insert-parent-input"}
               "Command:"]
              [:input#insert-parent-input
               {:type "text"
                :data-bind "insertCommand"
                :data-init "el.select()"
                :class "w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"}]]
             [:div.flex.justify-end.gap-2
              [modal/cancel-button {}]
              [modal/ok-button {}]]]}
     error (assoc :error error))])

(defn edit-label
  "Renders the edit label modal with optional error message."
  [id label error]
  [modal/modal
   (cond-> {:title "Edit Label"
            :signals {:editLabel label}
            :content
            [:form {:data-on:submit__stop (str "@post('/action/edit-label/" id "')")}
             [:div.mb-4
              [:label.block.text-sm.font-medium.text-gray-700.mb-2 {:for "edit-label-input"}
               "Label:"]
              [:input#edit-label-input
               {:type "text"
                :data-bind "editLabel"
                :data-init "el.select()"
                :class "w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"}]]
             [:div.flex.justify-end.gap-2
              [modal/cancel-button {}]
              [modal/ok-button {}]]]}
     error (assoc :error error))])

(defn progress
  "Renders a progress modal for tracking operation progress.
   
   Options:
   - :current - Current item number (1-indexed)
   - :total - Total number of items
   - :label - Optional label for the current item
   - :operation - Name of the operation being performed"
  [{:keys [current total label operation]}]
  [:div#modal-container
   {:class "fixed inset-0 z-50 flex items-center justify-center bg-black/60"}
   [:div {:class "bg-white rounded-lg shadow-xl max-w-md w-full mx-4"}
    ;; Header
    [:div {:class "px-6 py-4 border-b border-gray-200"}
     [:h3 {:class "text-lg font-medium text-gray-900"} operation]]
    ;; Body
    [:div {:class "px-6 py-4"}
     [:div {:class "mb-4"}
      [:div {:class "flex justify-between mb-2"}
       [:span {:class "text-sm font-medium text-gray-700"}
        (str current "/" total)]
       (when label
         [:span {:class "text-sm text-gray-600"} label])]]
     ;; Progress bar
     [:progress.progress.progress-primary.w-full
      {:value current :max total}]]
    ;; Footer with Cancel button aligned to the right
    [:div.flex.justify-end.mb-4.mr-4
     [:div.btn "Cancel"]]]])
