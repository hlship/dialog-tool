(ns dialog-tool.skein.ui.components.modal
  "Modal dialog component with backdrop and ESC key handling.")

(defn modal
  "Renders a modal dialog overlay.

   Options:
   - :title - Dialog title
   - :content - Hiccup content to render in the modal body

   The modal:
   - Can be dismissed by pressing ESC
   - Centers content and provides standard styling"
  [{:keys [title content]}]
  [:div.fixed.inset-0.z-50.flex.items-center.justify-center.bg-black.bg-opacity-50
   [:div.bg-white.rounded-lg.shadow-xl.max-w-md.w-full.mx-4
    {:data-on:click__stop ""
     :data-on:keydown "evt.key === 'Escape' && @post('/action/dismiss-modal')"}
    ;; Header
    [:div.px-6.py-4.border-b.border-gray-200
     [:h3.text-lg.font-medium.text-gray-900 title]]
    ;; Body
    [:div.px-6.py-4
     content]]])
