(ns dialog-tool.skein.ui.components.quit-modal
  "Quit confirmation modal component."
  (:require [dialog-tool.skein.ui.components.modal :as modal]))

(defn quit-modal
  "Renders a quit confirmation modal with options to cancel, save and quit, or quit without saving."
  []
  [modal/modal
   {:title "Unsaved Changes"
    :content
    [:div
     [:p.text-sm.text-gray-700.mb-4
      "You have unsaved changes. What would you like to do?"]
     [:div.flex.flex-col.gap-2
      [:button.w-full.px-4.py-2.text-sm.font-medium.text-white.bg-blue-700.rounded-md.hover:bg-blue-800
       {:type "button"
        :data-on:click "@post('/action/save-and-quit')"}
       "Save and Quit"]
      [:button.w-full.px-4.py-2.text-sm.font-medium.text-white.bg-red-600.rounded-md.hover:bg-red-700
       {:type "button"
        :data-on:click "@post('/action/quit-without-saving')"}
       "Quit Without Saving"]
      [modal/cancel-button {:label "Cancel"}]]]}])
