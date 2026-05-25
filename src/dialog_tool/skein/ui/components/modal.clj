(ns dialog-tool.skein.ui.components.modal
  "Modal dialog component with backdrop and ESC key handling."
  (:require [hyper.core :as h]))

(defn- default-cancel
  []
  (reset! (h/global-cursor :modal) nil))

(defn cancel-button
  "Renders a Cancel button that dismisses the modal.

   Options:
   - :label - Button text (default: 'Cancel')
   - :cancel - Function called when clicked, defaults to dismissing the modal"
  [{:keys [label cancel]
    :or   {label  "Cancel"
           cancel default-cancel}}]
  [:button.btn.btn-neutral
   {:type                "button"
    :data-on:click__stop (h/action {:as "cancel-modal"}
                                   (cancel))}
   label])

(defn ok-button
  "Renders an OK/submit button for modal forms.

   Options:
   - :label - Button text (default: 'OK')
   - :submit - optional callback invoked when clicked"
  [{:keys [label submit] :or {label "OK"}}]
  [:button.btn.btn-primary
   (cond-> {:type "submit"}
     submit (assoc :data-on:click__stop
                   (h/action {:as "modal:ok-submit"}
                             (submit))))
   label])

(defn modal
  "Renders a modal dialog overlay.

   Options:
   - :title     - Dialog title
   - :cancel    - Function to call if ESC invoked, default to dismissing the :modal global
   - :buttons   - Buttons to display at bottom of form
   - :error     - Optional error message to display at the top of the modal

   The modal:
   - Can be dismissed by pressing ESC (invoke cancel function)
   - Centers content and provides standard styling"
  [{:keys [title cancel buttons error]
    :or   {cancel default-cancel}}
   content]
  (let [keydown (when cancel
                  (h/action {:as "modal-key-press"}
                            ;; TODO: Add client-side guard?
                            (when (= $key "Escape")
                              (cancel))))]
    [:div#modal-container
     {:class "fixed inset-0 z-50 flex items-center justify-center bg-black/60"}
     [:div.bg-base-100.rounded-lg.shadow-xl.max-w-full.min-w-md.mx-4
      (cond-> {:data-on:click__stop "return"
               :tabindex            "-1"
               :data-init           "el.focus()"}
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
