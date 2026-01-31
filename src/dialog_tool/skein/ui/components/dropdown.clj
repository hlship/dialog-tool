(ns dialog-tool.skein.ui.components.dropdown
  (:require [clojure.string :as string]
            [dialog-tool.skein.ui.utils :refer [classes]]))

(defn dropdown
  "A dropdown using Daisy UI dropdown with details element for proper open/close control.

   Options:
   - :label - content to display in the trigger button
   - :disabled - when truthy, the dropdown button is disabled
   - :button-class - button style class (default: btn-primary)"
  [{:keys [label disabled button-class dropdown-class]
    :or   {label        "Drop Down"
           button-class "btn-primary"}} & items]
  [:details.dropdown.dropdown-left
   {:class          dropdown-class
    :data-on:toggle "if (el.open) {
                       const listener = (e) => {
                         if (!el.contains(e.target)) {
                           el.open = false;
                           document.removeEventListener('click', listener);
                         }
                       };
                       setTimeout(() => document.addEventListener('click', listener), 0);
                     }"}
   [:summary {:class    (classes "btn m-0" button-class)
              :disabled disabled}
    label]
   [:ul.menu.dropdown-content.bg-base-100.rounded-box.z-1.p-2.w-96.max-h-96.overflow-y-auto.columns-1
    {:class "shadow-xl/30"}
    items]])

(defn button
  "A button styled for use inside a dropdown menu. Styled per Tailwind Plus patterns.
   
   The sub-label, if provided, is placed in a paragraph tag below the main label.
  
  Options map may include:
  - :disabled - when truthy, the button is disabled
  - any other keys are passed through as HTML attributes"
  ([options label]
   (button options label nil))
  ([options label sub-label]
   (let [{:keys [disabled bg-class]} options
         attrs (cond-> (merge {:type                   "button"
                               :class                  bg-class
                               :data-on:click__capture "el.blur()"
                               :role                   "menuitem"
                               :tabindex               "-1"}
                              (dissoc options :disabled))
                 disabled (assoc :disabled true))]
     [:li
      {:class (when disabled "menu-disabled")}
      [:button attrs
       [:span label]
       (when sub-label
         [:<>
          [:br]
          [:span.text-xs.mt-0.5 sub-label]])]])))
