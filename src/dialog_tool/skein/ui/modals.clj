(ns dialog-tool.skein.ui.modals
  (:require [clojure.string :as string]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.ui.ansi :as ansi]
            [dialog-tool.skein.ui.components.modal :as modal]
            [hyper.core :as h]))

(defn edit-command
  "Renders the edit command modal."
  [cursor id command error]
  (modal/modal
   (cond-> {:title "Edit Command"
            :cursor cursor
            :buttons nil}
     error (assoc :error error))
   [:form {:data-on:submit__prevent
           (h/action
            (let [cmd (some-> (get $form-data "command") str string/trim)]
              (swap! cursor session/check-for-changed-sources)
              (let [[error session'] (session/edit-command! @cursor id cmd)]
                (reset! cursor session')
                (if error
                  (swap! cursor assoc :modal {:type :edit-command :knot-id id :error error})
                  (swap! cursor dissoc :modal)))))}
    [:div.mb-4
     [:label.block.text-sm.font-medium.text-gray-700.mb-2 {:for "edit-command-input"}
      "Command:"]
     [:input#edit-command-input
      {:type "text"
       :name "command"
       :value (or command "")
       :data-init "el.select()"
       :class "w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"}]]
    [:div.flex.justify-end.gap-2
     (modal/cancel-button {:cursor cursor})
     (modal/ok-button {})]]))

(defn insert-parent
  "Renders the insert parent modal."
  [cursor id command error]
  (modal/modal
   (cond-> {:title "Insert Parent"
            :cursor cursor
            :buttons nil}
     error (assoc :error error))
   [:form {:data-on:submit__prevent
           (h/action
            (let [cmd (some-> (get $form-data "command") str string/trim not-empty)]
              (swap! cursor session/check-for-changed-sources)
              (let [[error session'] (session/insert-parent! @cursor id cmd)]
                (reset! cursor session')
                (if error
                  (swap! cursor assoc :modal {:type :insert-parent :knot-id id :error error})
                  (swap! cursor dissoc :modal)))))}
    [:div.mb-4
     [:label.block.text-sm.font-medium.text-gray-700.mb-2 {:for "insert-parent-input"}
      "Command:"]
     [:input#insert-parent-input
      {:type "text"
       :name "command"
       :value (or command "")
       :data-init "el.select()"
       :class "w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"}]]
    [:div.flex.justify-end.gap-2
     (modal/cancel-button {:cursor cursor})
     (modal/ok-button {})]]))

(defn edit-label
  "Renders the edit label modal."
  [cursor id label locked error]
  (modal/modal
   (cond-> {:title "Edit Label"
            :cursor cursor
            :buttons nil}
     error (assoc :error error))
   [:form {:data-on:submit__prevent
           (h/action
            (let [lbl (some-> (get $form-data "label") str string/trim)
                  locked? (= "on" (get $form-data "locked"))
                  t (:tree @cursor)
                  existing (when-not (string/blank? lbl)
                             (tree/find-by-label t lbl))
                  duplicate? (and existing (not= id (:id existing)))]
              (if duplicate?
                (swap! cursor assoc :modal
                       {:type :edit-label :knot-id id
                        :error (str "Label \"" lbl "\" is already used by another knot.")})
                (do
                  (swap! cursor session/label id lbl locked?)
                  (swap! cursor dissoc :modal)))))}
    [:div.mb-4
     [:label.block.text-sm.font-medium.text-gray-700.mb-2 {:for "edit-label-input"}
      "Label:"]
     [:input#edit-label-input
      {:type "text"
       :name "label"
       :value (or label "")
       :data-init "el.select()"
       :class "w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"}]]
    [:div.mb-4.flex.items-center.gap-2
     [:input#edit-locked-input
      {:type "checkbox"
       :name "locked"
       :checked locked
       :class "checkbox"}]
     [:label.text-sm.font-medium.text-gray-700 {:for "edit-locked-input"}
      "Locked (prevent deletion)"]]
    [:div.flex.justify-end.gap-2
     (modal/cancel-button {:cursor cursor})
     (modal/ok-button {})]]))

(defn progress
  "Renders a progress modal for tracking operation progress."
  [cursor {:keys [current total label operation]}]
  (modal/modal
   {:title operation
    :cursor cursor
    :buttons (list (modal/cancel-button {:cursor cursor :label "Cancel"}))}
   [:div
    [:div {:class "flex justify-between mb-2"}
     [:span {:class "text-sm font-medium text-gray-700"}
      (str current "/" total)]
     (when label
       [:span {:class "text-sm text-gray-600"} label])]
    ;; Progress bar
    [:progress.progress.progress-primary.w-full
     {:value current :max total}]]))

(defn dynamic-state
  "Renders a modal dialog displaying the dynamic response."
  [cursor dynamic-response]
  (let [[_ trimmed] (string/split dynamic-response #"\n" 2)]
    (modal/modal
     {:title "Dynamic State"
      :cursor cursor
      :buttons (list (modal/cancel-button {:cursor cursor :label "OK"}))}
     [:div.whitespace-pre.text-sm.font-mono.overflow-y-auto.max-h-96
      {:data-init "el.focus()"}
      (ansi/ansi->hiccup trimmed)])))

(defn quit-modal
  "Renders a quit confirmation modal."
  [cursor]
  (modal/modal
   {:title "Unsaved Changes"
    :cursor cursor
    :buttons nil}
   [:div
    [:p.text-sm.text-gray-700.mb-4
     "You have unsaved changes. What would you like to do?"]
    [:div.flex.flex-col.gap-2
     [:button.btn.btn-primary
      {:type "button"
       :data-on:click (h/action
                       (swap! cursor session/save!)
                       ;; TODO: actual shutdown
                       (swap! cursor dissoc :modal))}
      "Save and Quit"]
     [:button.btn.btn-warning
      {:type "button"
       :data-on:click (h/action
                       ;; TODO: actual shutdown
                       (swap! cursor dissoc :modal))}
      "Quit Without Saving"]
     (modal/cancel-button {:cursor cursor})]]))
