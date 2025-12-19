(ns dialog-tool.skein.ui.components.dropdown
  (:require [dialog-tool.skein.ui.utils :refer [classes]]))

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
   "bg-white text-gray-900 shadow-sm"
   "ring-1 ring-inset ring-gray-300"
   ;; Hover state
   "hover:bg-gray-50"
   ;; Focus state - Tailwind Plus style ring
   "focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"))

(def ^:private menu-class
  "Tailwind Plus style dropdown menu."
  (classes
   ;; Width
   "w-56"
   ;; Max height with scrolling
   "max-h-64 overflow-y-auto"
   ;; Appearance - Tailwind Plus style
   "rounded-md bg-white shadow-lg"
   "ring-1 ring-black/5"
   ;; Focus handling
   "focus:outline-none"
   ;; Padding for menu items
   "py-1"))

(defn dropdown
  "A dropdown using Tailwind Plus el-dropdown and el-menu web components.
   
   Options:
   - :label - content to display in the trigger button
   - :placement - :left or :right (default :left), controls menu anchor position
   - :class - additional classes for the trigger button"
  [{:keys [label placement class]
    :or   {label     "Drop Down"
           placement :left}} & items]
  (let [anchor (if (= placement :right) "bottom start" "bottom end")]
    [:el-dropdown
     [:button {:type  "button"
               :class (classes trigger-class class)}
      label]
     (into [:el-menu {:popover true
                      :anchor  anchor
                      :class   menu-class}]
           items)]))

;; Tailwind Plus-style menu item button
(def ^:private button-class
  (classes
   ;; Layout
   "block w-full text-left"
   ;; Sizing & typography  
   "px-4 py-2 text-sm"
   ;; Colors - Tailwind Plus style
   "text-gray-700"
   ;; Hover state - subtle gray background
   "hover:bg-gray-100 hover:text-gray-900"
   ;; Focus state
   "focus:bg-gray-100 focus:text-gray-900 focus:outline-none"
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
   (let [{:keys [disabled]} options
         attrs (cond-> (merge {:type     "button"
                               :class    button-class
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