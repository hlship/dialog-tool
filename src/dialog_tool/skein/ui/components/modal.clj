(ns dialog-tool.skein.ui.components.modal
  "Modal dialog component with backdrop and ESC key handling."
  (:require [hyper.core :as h]))

(defn cancel-button
  "Renders a Cancel button that dismisses the modal.

   Options:
   - :label - Button text (default: 'Cancel')
   - :cursor - The session cursor (required for dismissal)"
  [{:keys [label cursor] :or {label "Cancel"}}]
  [:button.btn.btn-neutral
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
   - :title     - Dialog title
   - :cursor    - The session cursor; enables ESC to dismiss by clearing :modal
   - :on-escape - Explicit data-on:keydown action string (overrides :cursor ESC handling)
   - :buttons   - Buttons to display at bottom of form
   - :error     - Optional error message to display at the top of the modal

   The modal:
   - Can be dismissed by pressing ESC (when :cursor or :on-escape is provided)
   - Centers content and provides standard styling"
  [{:keys [title cursor on-escape buttons error]}
   content]
  (let [keydown (or on-escape
                    (when cursor
                      (h/action
                       (when (= $key "Escape")
                         (swap! cursor dissoc :modal)))))]
    [:div#modal-container
     {:class "fixed inset-0 z-50 flex items-center justify-center bg-black/60"}
     [:div.bg-base-100.rounded-lg.shadow-xl.max-w-full.min-w-md.mx-4
      (cond-> {:data-on:click__stop "return"
               :tabindex "-1"
               :data-init "el.focus()"}
        keydown (assoc :data-on:keydown keydown))
      ;; Header
      [:div.px-6.py-4.border-b.border-base-200
       [:h3.text-lg.font-medium.text-base-content title]]
      ;; Error message (if present)
      (when error
        [:div.px-6.pt-4
         [:div.alert.alert-error.text-sm
          [:p.text-sm error]]])
      ;; Body
      [:div.px-6.py-4
       content
       (when buttons
         [:div.flex.justify-end.gap-2 buttons])]]]))
