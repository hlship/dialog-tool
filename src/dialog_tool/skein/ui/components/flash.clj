(ns dialog-tool.skein.ui.components.flash
  "Flash message component for displaying temporary notifications.")

(defn flash-message
  "Renders a flash message that fades out automatically after 0.5 seconds.
   
   Parameters:
   - message: The text to display"
  [message]
  [:div {:class "fixed top-20 left-1/2 -translate-x-1/2 z-50 pointer-events-none"}
   [:div {:id (str "flash-" (random-uuid))
          :class "bg-blue-600 text-white px-6 py-3 rounded-lg shadow-lg transition-opacity duration-500"
          :data-init "el.style.opacity = '1'; setTimeout(() => el.style.opacity = '0', 500)"}
    message]])
