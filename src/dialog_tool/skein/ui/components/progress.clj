(ns dialog-tool.skein.ui.components.progress
  "Progress modal component for long-running operations.")

(defn progress-modal
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
         [:span {:class "text-sm text-gray-600"} label])]
      ;; Progress bar
      [:div {:class "w-full bg-gray-200 rounded-full h-2.5"}
       [:div {:class "bg-blue-600 h-2.5 rounded-full"
              :style (str "width: " (* 100 (/ current total)) "%")}]]]
     [:div {:class "flex justify-end"}
      [:button {:type "button"
                :disabled true
                :class "px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50"}
       "Cancel"]]]]])
