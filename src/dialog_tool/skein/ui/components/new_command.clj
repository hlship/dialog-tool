(ns dialog-tool.skein.ui.components.new-command
  "Component for entering a new command to add as a child node."
  (:require [clojure.string :as string]
            [dialog-tool.skein.session :as session]
            [hyper.core :as h]))

(defn- normalize-input
  [s]
  (-> s str string/trim (string/replace #"\s+" " ")))

(defn new-command-input
  "Renders an input field for entering new commands.
   When submitted (on Enter), sends the command to create a new node
   as a child of the given parent knot."
  [cursor parent-knot-id]
  (let [command-signal (h/local-signal :new-command "")]
    [:div.mt-4.mb-8
     [:div.flex.items-center.gap-2
      [:span.text-gray-400 ">"]
      [:input#new-command-input {:type "text"
                                 :name "command"
                                 :placeholder "Enter command..."
                                 :class "flex-1 rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"
                                 :data-bind (:name command-signal)
                                 :data-on:change (h/action
                                                  (let [cmd (some-> $value normalize-input)]
                                                    (when cmd
                                                      (swap! cursor session/check-for-changed-sources)
                                                      (swap! cursor session/command! parent-knot-id cmd))))}]]]))
