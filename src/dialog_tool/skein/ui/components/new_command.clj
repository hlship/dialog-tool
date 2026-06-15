(ns dialog-tool.skein.ui.components.new-command
  "Component for entering a new command to add as a child node."
  (:require [dialog-tool.env :as env]
            [dialog-tool.skein.ui.common :as common]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.ui.js :as js]
            [hyper.core :as h]))

(defn- apply-new-command
  [*session parent-knot-id command]
  (swap! *session (fn [session]
                    (-> session
                        session/capture-undo
                        session/check-for-changed-sources
                        (session/command! parent-knot-id command)
                        common/maybe-apply-source-error
                        js/navigate-to-active-knot!))))

(defn- process-new-command
  [*session parent-knot-id command-text]
  (let [normalized (some-> command-text common/normalize-input)]
    (when normalized
      (env/log-action "command" parent-knot-id " " (pr-str normalized))
      (apply-new-command *session parent-knot-id normalized))))

(defn- process-new-keystroke-command
  [*session parent-knot-id key]
  (env/log-action "keystroke" parent-knot-id " " (pr-str key))
  ;; The session layer knows about "space" "enter" and "backspace"
  (let [key' (get {" "     "space"
                   "Enter" "enter"} key key)]
    (apply-new-command *session parent-knot-id key')))

(defn new-keystroke-input
  [*session parent-knot-id]
  (let [input-signal (h/local-signal :new-keystroke "")]
    [:div.mt-4.mb-8
     [:div.flex.items-center.gap-2
      [:span.text-gray-400 {:aria-hidden "true"} "Key:"]
      [:input#new-command-input
       {:type             "text"
        :name             "keystroke"
        :class            "rounded-md border-base-300 shadow-sm focus:border-primary focus:ring-primary sm:text-sm p-2 border"
        :maxlength        1
        :minlength        1
        :data-bind        (:name input-signal)
        :data-on:keypress (h/action
                            {:as "new-keystroke"}
                            (process-new-keystroke-command *session parent-knot-id $key))}]
      " or "
      (map (fn [label]
             [:button.btn.btn-sm
              {:type          :submit
               :data-on:click (h/action
                                {:as "new-keystroke:special"}
                                (process-new-keystroke-command *session parent-knot-id label))}
              label])
           ["enter" "space" "backspace"])]]))

(defn new-command-input
  "Renders an input field for entering new commands.
   When submitted (on Enter), sends the command to create a new node
   as a child of the given parent knot."
  [*session parent-knot-id]
  (let [command-signal (h/local-signal :new-command "")]
    [:div.mt-4.mb-8
     [:div.flex.items-center.gap-2
      [:span.text-gray-400 {:aria-hidden "true"} ">"]
      [:input#new-command-input
       {:type           "text"
        :name           "command"
        :aria-label     "Enter command"
        :placeholder    "Enter command..."
        :class          "flex-1 rounded-md border-base-300 shadow-sm focus:border-primary focus:ring-primary sm:text-sm p-2 border"
        :data-bind      (:name command-signal)
        :data-on:change (h/action
                          {:as "new-command"}
                          (process-new-command *session parent-knot-id $value))}]]]))
