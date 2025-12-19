(ns dialog-tool.skein.ui.components.dropdown
  (:require [dialog-tool.skein.ui.utils :as utils :refer [classes]]
            [clojure.string :as string]))

(defn- escape
  "Escape a string for use in a CSS class name."
  [s]
  (if (re-matches #"(?ix) (_|a-z)(_|a-z|0-9)*" s)
    s
    (str \' s \') ; TODO: Escape any quotes?  
    ))

(defn dynamic-classes
  [m]
  (str "{"
       (->> m
            (map (fn [[k v]] (str (-> k name escape) ":" v)))
            (string/join ","))
       "}"))

(defn dropdown-trigger
  "A small button to trigger a dropdown menu. Returns just the button element.
   
   Options:
   - :id - unique id for the button (required, used to link to dropdown-menu)
   - :label - content to display in the button (typically an icon)
   - :class - additional CSS classes for the button"
  [{:keys [id label class]}]
  [:button {:id            id
            :type          "button"
            :class         (classes "text-center font-medium focus-within:ring-4"
                                    "focus-within:outline-none"
                                    "inline-flex items-center justify-center py-2"
                                    "text-xs text-gray-900 bg-white"
                                    "border border-gray-300 focus-within:ring-gray-200"
                                    "rounded-lg hover:bg-slate-200"
                                    class)
            :data-on:click "const r = toggleDropdown(el, evt, $_activeDropdown); $_activeDropdown = r.activeDropdown; $_dropdownFlipped = r.dropdownFlipped"
            :aria-haspopup "true"
            :data-class    (dynamic-classes {:aria-expanded "$_activeDropdown === el.id"})}
   label])

(defn dropdown-menu
  "A dropdown menu that appears when triggered. Returns just the menu element.
   Should be a sibling of dropdown-trigger.
   
   Options:
   - :id - the id of the associated dropdown-trigger button
   - :placement - :left or :right (default :left)"
  [{:keys [id placement]
    :or   {placement :left}} & items]
  (let [position-class (if (= placement :right) "left-0" "right-0")]
    [:div {:class             (classes "absolute z-10 w-96 rounded-md bg-slate-100 shadow-lg"
                                       "ring-1 ring-black/5 focus:outline-none hidden"
                                       position-class)
           :data-class        (dynamic-classes
                               {:top-full "!$_dropdownFlipped"
                                :mt-2     "!$_dropdownFlipped"
                                :top-auto "$_dropdownFlipped"
                                :bottom-full "$_dropdownFlipped"
                                :mb-2     "$_dropdownFlipped"
                                :hidden (str "$_activeDropdown != '" id "'")})
           :role              "menu"
           :aria-labelledby   id
           :data-on:click__outside (str "$_activeDropdown = closeDropdownOutside(evt, '" id "', $_activeDropdown)")}
     [:div {:class         "py-1"
            :data-on:click "$_activeDropdown = false"}
      items]]))

(defn dropdown
  "A combined dropdown with trigger button and menu. Wraps both in a relative container.
   
   Options:
   - :label - content to display in the trigger button
   - :id - unique id (auto-generated if not provided)"
  [{:keys [label id]
    :or   {label "Drop Down"
           id    (utils/unique-id "dropdown")}} & items]
  [:div.relative
   [dropdown-trigger {:id id :label label}]
   (into [dropdown-menu {:id id}] items)])

(def ^:private button-class
  (classes "block text-left w-full text-gray-700"
           "px-4 py-2 text-sm"
           "hover:bg-slate-200"
           "disabled:text-gray-400 disabled:cursor-not-allowed disabled:hover:bg-transparent"))

(defn button
  "A button styled for use inside a dropdown menu.
   
   The sub-label, if provided, is placed in a paragraph tag below the main label.
  
  Options map may include:
  - :disabled - when truthy, the button is disabled
  - any other keys are passed through as HTML attributes"
  ([options label]
   (button options label nil))
  ([options label sub-label]
   (let [{:keys [disabled]} options
         attrs (cond-> (merge {:type  "button"
                               :class button-class
                               :role  "menuitem"}
                              (dissoc options :disabled))
                 disabled (assoc :disabled true))]
     [:button attrs label
      (when sub-label
        [:p.text-xs.font-normal
         {:class (if  disabled "text-gray-400" "text-gray-700")}
         sub-label])])))