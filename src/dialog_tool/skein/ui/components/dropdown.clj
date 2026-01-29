(ns dialog-tool.skein.ui.components.dropdown
  (:require [clojure.string :as string]
            [dialog-tool.skein.ui.utils :refer [classes]]))

;; Tailwind Plus web components dropdown
;; Uses el-dropdown and el-menu from @tailwindplus/elements
;; See: https://tailwindcss.com/plus/ui-blocks/application-ui/elements/dropdowns

(def ^:private trigger-class
  "Tailwind Plus style trigger button."
  (classes
    ;; Base layout
    "inline-flex items-center justify-center gap-x-1.5"
    ;; Sizing & typography
    "rounded-md px-3 py-2 text-sm font-semibold"
    ;; Colors - white background with subtle border
    "text-gray-900 shadow-sm"
    "ring-1 ring-inset ring-gray-300"
    ;; Focus state - Tailwind Plus style ring
    "focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"))

(def ^:private menu-class
  "Tailwind Plus style dropdown menu."
  (classes
    ;; Width
    "w-64"
    ;; Max height with scrolling
    "max-h-96 overflow-y-auto"
    "rounded-md bg-white shadow-lg"
    "ring-1 ring-black/5"
    ;; Focus handling
    "focus:outline-none"
    ;; Spacing between trigger and menu
    "[--anchor-gap:--spacing(2)]"
    ;; Animate
    "transition-opacity"
    "data-closed:opacity-0 data-enter:duration-200 data-enter:ease-out"
    "data-leave:duration-75 data-leave:ease-in"
    "py-1"))

(defn dropdown
  "A dropdown using Tailwind Plus el-dropdown and el-menu web components.

   Options:
   - :label - content to display in the trigger button
   - :class - additional classes to merge with default trigger-class
   - :button-class - completely replaces the default trigger-class styling
   - :bg-class - background color class to override default bg-white (e.g., 'bg-red-300 hover:...')
   - :disabled - when truthy, the dropdown button is disabled"
  [{:keys         [label button-class bg-class disabled]
    extra-classes :class
    :or           {label        "Drop Down"
                   bg-class     "bg-white hover:bg-gray-50"
                   button-class trigger-class}} & items]
  [:el-dropdown.inline-block
   [:button (cond-> {:type  "button"
                     :class (classes bg-class button-class extra-classes)}
              disabled (assoc :disabled true))
    label]
   (into [:el-menu {:anchor "left"
                    :class  menu-class}]
         items)])

;; Tailwind Plus-style menu item button
(def ^:private button-class
  (classes
    ;; Layout
    "block w-full text-left"
    ;; Sizing & typography  
    "px-4 py-2 text-sm"
    ;; Colors - Tailwind Plus style
    "text-gray-700"
    ;; Disabled state
    "disabled:text-gray-400 disabled:cursor-not-allowed disabled:hover:bg-transparent"))

(defn button
  "A button styled for use inside a dropdown menu. Styled per Tailwind Plus patterns.
   
   The sub-label, if provided, is placed in a paragraph tag below the main label.
  
  Options map may include:
  - :disabled - when truthy, the button is disabled
  - any other keys are passed through as HTML attributes"
  ([options label]
   (button options label nil))
  ([options label sub-label]
   (let [{:keys [disabled bg-class]
          :or   {bg-class "hover:bg-gray-100 hover:text-gray-900"}} options
         attrs (cond-> (merge {:type     "button"
                               :class    (classes bg-class button-class)
                               :role     "menuitem"
                               :tabindex "-1"}
                              (dissoc options :disabled))
                 disabled (assoc :disabled true))]
     [:button attrs
      [:span label]
      (when sub-label
        [:p.text-xs.mt-0.5
         {:class (if disabled "text-gray-400" "text-gray-500")}
         sub-label])])))
