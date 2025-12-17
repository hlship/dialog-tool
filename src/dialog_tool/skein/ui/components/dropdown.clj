(ns dialog-tool.skein.ui.components.dropdown
  (:require [dialog-tool.skein.ui.utils :as utils]
            [clojure.string :as string]))

(defn- escape
  "Escape a string for use in a CSS class name."
  [s]
  (if (re-matches #"(?ix) (\p{Alnum}|_)+" s)
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

(defn dropdown
  [{:keys [label id]
    :or   {label "Drop Down"
           id    (utils/unique-id "dropdown")}} & items]
  [:div {:class              "relative inline-block text-left"
         :data-on:click__outside "if ($_activeDropdown === el.firstChild.id) { $_activeDropdown = false }"
         :data-dropdown-root true}

   [:button {:id                       id
             :type                     "button"
             :class                    "inline-flex w-full justify-between gap-2 rounded-md bg-white px-3 py-2 text-sm font-medium text-gray-700 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-indigo-500"
             ;; Set _activeDropdown to false because null removes the signal rather than propagates a new value
             ;; When opening, also calculate if the dropdown should flip above the button
             :data-on:click            "if ($_activeDropdown === el.id) { $_activeDropdown = false } else { $_activeDropdown = el.id; $_dropdownFlipped = shouldFlipDropdown(evt) }"
             :aria-haspopup            "true"
             :data-class (dynamic-classes {:aria-expanded "$_activeDropdown === el.id"})
             :data-dropdown-button     true}
    label]
   ;; Dropdown menu: positioned below by default, flipped above when near bottom edge
   [:div {:class                  "absolute right-0 z-10 w-56 rounded-md bg-white shadow-lg ring-1 ring-black/5 focus:outline-none hidden"
          :data-class:hidden      "$_activeDropdown !== el.previousElementSibling.id"
          ;; Below button (not flipped)
          :data-class (dynamic-classes
                       {:top-full "!$_dropdownFlipped"
                        :mt-2 "!$_dropdownFlipped"
                        :top-auto "$_dropdownFlipped"
                        :bottom-full "$_dropdownFlipped"
                        :mb-2 "$_dropdownFlipped"})
          :role                   "menu"
          :aria-labelledby        id
          :data-dropdown-menu     true}
    ;; Default is that clicking on a button inside the dropdown closes the dropdown
    [:div {:class         "py-1"
           :data-on:click "$_activeDropdown = false"}
     items]]])

(def ^:private button-class
  "block w-full py-2 text-left text-sm text-gray-700 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:bg-transparent")

(defn button
  "A button styled for use inside a dropdown menu.
  
  Options map may include:
  - :disabled - when truthy, the button is disabled
  - any other keys are passed through as HTML attributes"
  [options label]
  (let [{:keys [disabled]} options
        attrs (cond-> (merge {:type  "button"
                              :class button-class
                              :role  "menuitem"}
                             (dissoc options :disabled))
                disabled (assoc :disabled true))]
    [:button attrs label]))

