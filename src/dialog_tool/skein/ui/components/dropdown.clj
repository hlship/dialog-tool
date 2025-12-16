(ns dialog-tool.skein.ui.components.dropdown
  (:require [cheshire.core :as json]
            [dialog-tool.skein.ui.utils :as utils]))

(defn dropdown
  [{:keys [label id]
    :or   {label "Drop Down"
           id    (utils/unique-id "dropdown")}} & items]
  (let [signal  (str "_" id "_Open")
        $signal (str "$" signal)]
    [:div {:class              "relative inline-block text-left" 
           :data-on:click__outside (str $signal " = false")
           :data-dropdown-root true}

     [:button {:type                     "button"
               :class                    "inline-flex w-full justify-between gap-2 rounded-md bg-white px-3 py-2 text-sm font-medium text-gray-700 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-indigo-500"
               :data-on:click            (str $signal "=!" $signal)
               :aria-haspopup            "true"
               :data-class:aria-expanded $signal
               :data-dropdown-button     true}
      label]
     [:div {:class              "absolute right-0 z-10 mt-2 w-56 origin-top-right rounded-md bg-white shadow-lg ring-1 ring-black/5 focus:outline-none hidden"
            :data-class:hidden  (str "!" $signal)
            :role               "menu"
            :aria-labelledby    "dropdownButton"            ; purely ARIA; doesn't need to match an id
            :data-dropdown-menu true}
      ;; Default is that clicking on a button inside the dropdown closes the dropdown
      [:div {:class "py-1"
             :data-on:click (str $signal " = false")}
       items]]]))

;; TODO: Maybe a smarter version of merge that knows how to eliminate conflicts

(defn button
  "A button styled for use inside a dropdown menu."
  [attrs label]
  [:button (merge {:type  "button"
                   :class "block w-full py-2 text-left text-sm text-gray-700 hover:bg-gray-100"
                   :role  "menuitem"}
                  attrs)
   label])

