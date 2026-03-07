(ns dialog-tool.skein.ui.components.flash
  "Flash message component for displaying temporary notifications.
   
   Supports two types:
   - :info (default) - auto-fading blue notification for success/progress
   - :error - persistent red notification with close button, requires user dismissal")

(defn flash-message
  "Renders a flash message notification.

   flash can be:
   - a string (treated as :info type, auto-fades)
   - a map with :message and :type (:info or :error)

   :info messages auto-fade after 0.5 seconds.
   :error messages persist until the user clicks the close button."
  [flash]
  (let [{:keys [message type]} (if (string? flash)
                                 {:message flash :type :info}
                                 flash)
        error? (= type :error)
        id (str "flash-" (random-uuid))]
    (let [remove-script (str "document.getElementById('" id "').parentElement.remove()")]
      [:div {:class "fixed top-20 left-1/2 -translate-x-1/2 z-50"
             :style {:pointer-events (if error? "auto" "none")}}
       [:div (cond-> {:id id
                      :class (if error?
                               "flex items-center gap-3 bg-red-600 text-white px-6 py-3 rounded-lg shadow-lg"
                               "bg-blue-600 text-white px-6 py-3 rounded-lg shadow-lg transition-opacity duration-500")}
               error? (assoc :tabindex "-1"
                             :data-init "el.focus()"
                             :onkeydown (str "if(event.key==='Escape'){" remove-script "}"))
               (not error?) (assoc :data-init "el.style.opacity = '1'; setTimeout(() => el.style.opacity = '0', 500)"))
        [:span message]
        (when error?
          [:button {:type "button"
                    :class "ml-2 text-white/80 hover:text-white text-lg font-bold cursor-pointer"
                    :onclick remove-script}
           "✕"])]])))
