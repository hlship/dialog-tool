(ns dialog-tool.skein.ui.components.modal
  "Modal dialog component with backdrop and ESC key handling."
  (:require [hyper.core :as h]))

(defn cancel-button
  "Renders a Cancel button that dismisses the modal.

   Options:
   - :label - Button text (default: 'Cancel')
   - :cursor - The session cursor (required for dismissal)"
  [{:keys [label cursor] :or {label "Cancel"}}]
  [:button.btn
   {:type "button"
    :data-on:click__stop (h/action (swap! cursor dissoc :modal))}
   label])

(defn ok-button
  "Renders an OK/submit button for modal forms.

   Options:
   - :label - Button text (default: 'OK')"
  [{:keys [label] :or {label "OK"}}]
  [:button.btn.btn-primary
   {:type "submit"}
   label])

(defn modal
  "Renders a modal dialog overlay.

   Options:
   - :title - Dialog title
   - :cursor - The session cursor (for ESC dismissal)
   - :buttons - Buttons to display at bottom of form
   - :error - Optional error message to display at the top of the modal

   The modal:
   - Can be dismissed by pressing ESC
   - Centers content and provides standard styling"
  [{:keys [title cursor buttons error]}
   content]
  [:div#modal-container
   {:class "fixed inset-0 z-50 flex items-center justify-center bg-black/60"}
   [:div.bg-white.rounded-lg.shadow-xl.max-w-full.min-w-md.mx-4
    {:data-on:click__stop "return"
     :data-on:keydown (h/action
                       (when (= $key "Escape")
                         (swap! cursor dissoc :modal)))
     :tabindex "-1"
     :data-init "el.focus()"}
    ;; Header
    [:div.px-6.py-4.border-b.border-gray-200
     [:h3.text-lg.font-medium.text-gray-900 title]]
    ;; Error message (if present)
    (when error
      [:div.px-6.pt-4
       [:div.bg-red-50.border.border-red-200.text-red-800.px-4.py-3.rounded
        [:p.text-sm error]]])
    ;; Body
    [:div.px-6.py-4
     content
     (when buttons
       [:div.flex.justify-end.gap-2 buttons])]]])
