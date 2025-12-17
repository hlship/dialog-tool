(ns dialog-tool.skein.ui.components.dropdown
  (:require [dialog-tool.skein.ui.utils :as utils]))

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
             :data-class:aria-expanded "$_activeDropdown === el.id"
             :data-dropdown-button     true}
    label]
   ;; Dropdown menu: positioned below by default, flipped above when near bottom edge
   [:div {:class                  "absolute right-0 z-10 w-56 rounded-md bg-white shadow-lg ring-1 ring-black/5 focus:outline-none hidden"
          :data-class:hidden      "$_activeDropdown !== el.previousElementSibling.id"
          ;; Below button (not flipped)
          :data-class:top-full    "!$_dropdownFlipped"
          :data-class:mt-2        "!$_dropdownFlipped"
          ;; Above button (flipped) - need top-auto to reset top positioning
          :data-class:top-auto    "$_dropdownFlipped"
          :data-class:bottom-full "$_dropdownFlipped"
          :data-class:mb-2        "$_dropdownFlipped"
          :role                   "menu"
          :aria-labelledby        id
          :data-dropdown-menu     true}
    ;; Default is that clicking on a button inside the dropdown closes the dropdown
    [:div {:class         "py-1"
           :data-on:click "$_activeDropdown = false"}
     items]]])

;; TODO: Maybe a smarter version of merge that knows how to eliminate conflicts

(defn button
  "A button styled for use inside a dropdown menu."
  [attrs label]
  [:button (merge {:type  "button"
                   :class "block w-full py-2 text-left text-sm text-gray-700 hover:bg-gray-100"
                   :role  "menuitem"}
                  attrs)
   label])

