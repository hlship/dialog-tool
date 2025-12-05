(ns dialog-tool.skein.ui.components
  (:require [cheshire.core :as json]))

(def *id (atom 0))

(defn unique-id
  [prefix]
  (str prefix (swap! *id inc)))

(defn dropdown
  [{:keys [options post-url label id]
    :or   {label "Select an option"
           id    (unique-id "dropdown")}} & items]
  (let [signal  (str "_" id "_Open")
        $signal (str "$" signal)]
    [:div {:class              "relative inline-block text-left"
           :data-signals       (json/generate-string {id false})
           :data-dropdown-root true}

     [:button {:type                     "button"
               :class                    "inline-flex w-full justify-between gap-2 rounded-md bg-white px-3 py-2 text-sm font-medium text-gray-700 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-indigo-500"
               :data-on:click            (str $signal "=!" $signal)
               :aria-haspopup            "true"
               :data-class:aria-expanded $signal
               :data-dropdown-button     true}
      [:span {:data-dropdown-label true} label]
      [:svg {:class       "h-4 w-4 text-gray-500"
             :viewBox     "0 0 20 20"
             :fill        "currentColor"
             :aria-hidden "true"}
       [:path {:fill-rule "evenodd"
               :d         "M5.23 7.21a.75.75 0 011.06.02L10 10.94l3.71-3.71a.75.75 0 111.08 1.04l-4.25 4.25a.75.75 0 01-1.08 0L5.21 8.27a.75.75 0 01.02-1.06z"
               :clip-rule "evenodd"}]]]
     [:div {:class              "absolute right-0 z-10 mt-2 w-56 origin-top-right rounded-md bg-white shadow-lg ring-1 ring-black/5 focus:outline-none hidden"
            :data-class:hidden  (str "!" $signal)
            :role               "menu"
            :aria-labelledby    "dropdownButton"            ; purely ARIA; doesn't need to match an id
            :data-dropdown-menu true}
      [:div {:class "py-1"}
       items
       (for [v options]
         ^{:key v}
         [:button {:type                 "button"
                   :class                "block w-full px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-100"
                   :data-dropdown-option v
                   :data-on:click        (format "@post('%s', { body: { value: '%s' } })"
                                                 post-url v)
                   :role                 "menuitem"}
          v])]]]))

;; TODO: Maybe a smarter version of merge that knows how to eliminate conflicts

(defn dropdown-item
  [attrs label]
  [:button (merge {:type  "button"
                   :class "block w-full px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-100"
                   :role  "menuitem"}
                  attrs)
   label])
