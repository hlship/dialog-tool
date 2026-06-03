(ns dialog-tool.skein.ui.components.new-command
  "Component for entering a new command to add as a child node."
  (:require [dialog-tool.env :as env]
            [dialog-tool.skein.ui.common :as common]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.ui.js :as js]
            [hyper.core :as h]))

(defn- process-new-command
  [cursor parent-knot-id command-text]
  (let [normalized (some-> command-text common/normalize-input)]
    (when normalized
      (env/log-action "command"  parent-knot-id " " (pr-str normalized))
      (swap! cursor (fn [session]
                      (-> session
                          session/capture-undo
                          session/check-for-changed-sources
                          (session/command! parent-knot-id normalized)
                          common/maybe-apply-source-error
                          js/navigate-to-active-knot!))))))


(defn new-command-input
  "Renders an input field for entering new commands.
   When submitted (on Enter), sends the command to create a new node
   as a child of the given parent knot."
  [cursor parent-knot-id]
  (let [command-signal (h/local-signal :new-command "")]
    [:div.mt-4.mb-8
     [:div.flex.items-center.gap-2
      [:span.text-gray-400 {:aria-hidden "true"} ">"]
      [:input#new-command-input
       {:type        "text"
        :name        "command"
        :aria-label  "Enter command"
        :placeholder "Enter command..."
        :class       "flex-1 rounded-md border-base-300 shadow-sm focus:border-primary focus:ring-primary sm:text-sm p-2 border"
        :data-bind   (:name command-signal)
        :data-on:change (h/action
                          (process-new-command cursor parent-knot-id $value))}]]]))
