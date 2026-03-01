(ns dialog-tool.skein.ui.components.dropdown
  (:require [dialog-tool.skein.ui.utils :refer [classes]]))

(defn dropdown
  "A dropdown using the HTML Popover API for proper layering above sticky elements.
   Each dropdown generates a unique ID to link the trigger button to its popover menu.

   Options:
   - :label - content to display in the trigger button
   - :disabled - when truthy, the dropdown button is disabled
   - :button-class - button style class (default: btn-primary)"
  [{:keys [label disabled button-class]
    :or {label "Drop Down"
         button-class "btn-primary"}} & items]
  (let [id (str "dd-" (random-uuid))]
    [:div
     [:button {:class (classes "btn m-0" button-class)
               :disabled disabled
               :popovertarget id}
      label]
     [:ul.menu.bg-base-100.rounded-box.p-2.w-96.max-h-96.overflow-y-auto.flex-nowrap
      {:id id
       :popover "auto"
       :class "shadow-xl/30"
       :data-on:toggle "if(evt.newState==='open') positionDropdown(el)"
       :data-on:click "el.hidePopover()"}
      items]]))

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
         attrs (cond-> (merge {:type "button"
                               :class bg-class
                               :role "menuitem"
                               :tabindex "-1"}
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
