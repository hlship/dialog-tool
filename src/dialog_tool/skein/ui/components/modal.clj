(ns dialog-tool.skein.ui.components.modal
  "Modal dialog component with backdrop and ESC key handling."
  (:require [cheshire.core]))

(defn cancel-button
  "Renders a Cancel button that dismisses the modal.

   Options:
   - :label - Button text (default: 'Cancel')"
  [{:keys [label] :or {label "Cancel"}}]
  [:button.px-4.py-2.text-sm.font-medium.text-gray-700.bg-white.border.border-gray-300.rounded-md.hover:bg-gray-50
   {:type "button"
    :data-on:click__stop "@post('/action/dismiss-modal')"}
   label])

(defn ok-button
  "Renders an OK/submit button for modal forms.

   Options:
   - :label - Button text (default: 'OK')"
  [{:keys [label] :or {label "OK"}}]
  [:button.px-4.py-2.text-sm.font-medium.text-white.bg-blue-700.rounded-md.hover:bg-blue-800
   {:type "submit"}
   label])

(defn modal
  "Renders a modal dialog overlay.

   Options:
   - :title - Dialog title
   - :content - Hiccup content to render in the modal body
   - :signals - Optional map of signals to initialize (will be JSON-encoded as data-signals)
   - :error - Optional error message to display at the top of the modal

   The modal:
   - Can be dismissed by pressing ESC
   - Centers content and provides standard styling"
  [{:keys [title content signals error]}]
  [:div#modal-container
   (merge
    {:class "fixed inset-0 z-50 flex items-center justify-center bg-black/60"}
    (when signals
      {:data-signals (cheshire.core/generate-string signals)}))
   [:div.bg-white.rounded-lg.shadow-xl.max-w-md.w-full.mx-4
    {:data-on:click__stop ""
     :data-on:keydown "evt.key === 'Escape' && @post('/action/dismiss-modal')"}
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
     content]]])
