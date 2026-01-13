(ns dialog-tool.skein.ui.components.new-command
  "Component for entering a new command to add as a child node.")

(defn new-command-input
  "Renders an input field for entering new commands.
   When submitted (on Enter), sends the command to create a new node
   as a child of the session's active knot.
   
   The component binds to the newCommand Datastar signal.
   
   When scroll-to? is true, the component will be scrolled into view
   after rendering (typically after a new command is submitted)."
  [scroll-to?]
  [:div.mt-4.mb-8
   (when scroll-to?
     {:data-scroll-into-view true})
   [:div.flex.items-center.gap-2
    [:span.text-gray-400 ">"]
    [:input {:type        "text"
             :name        "command"
             :placeholder "Enter command..."
             :class       "flex-1 rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"
             :data-bind   "newCommand" 
             :data-on:change "@post('/action/new-command')"}]]])

