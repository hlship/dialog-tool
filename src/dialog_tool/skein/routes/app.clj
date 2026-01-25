(ns dialog-tool.skein.routes.app
  (:require [clojure.string :as string]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.ui.app :as ui.app]
            [dialog-tool.skein.ui.components.modal :as modal]
            [dialog-tool.skein.ui.components.progress :as progress]
            [dialog-tool.skein.ui.utils :as utils]
            [huff2.core :refer [html]]))

(defn render-app
  ([request]
   (render-app request nil))
  ([request opts]
   {:status 200
    :body (html (ui.app/render-app request opts))}))

;;; Action handlers
;;; Each receives :signals (parsed Datastar signals) in the request

(defn knot-id
  "Extracts the knot id from the first path parameter."
  [{:keys [path-params]}]
  (-> path-params first parse-long))

(defn new-command
  "Adds a new command to the tree as a child of the active knot."
  [{:keys [*session signals] :as request}]
  (let [{:keys [newCommand]} signals
        command (some-> newCommand str string/trim not-empty)]
    (when command
      (swap! *session session/command! command))
    (render-app request {:scroll-to-new-command? true
                         :reset-command-input? true})))

(defn bless-knot
  "Blesses the specified knot, copying its unblessed response to be the blessed response."
  [{:keys [*session] :as request}]
  (swap! *session session/bless (knot-id request))
  (render-app request {:flash "Blessed"}))

(defn bless-to-knot
  "Blesses all knots from root to the specified knot, inclusive."
  [{:keys [*session] :as request}]
  (swap! *session session/bless-to (knot-id request))
  (render-app request {:flash "Blessed to here"}))

(defn replay-to-knot
  "Replays from the start to the specified knot."
  [{:keys [*session] :as request}]
  (swap! *session session/replay-to! (knot-id request))
  (render-app request {:flash "Replayed"}))

(defn select-knot
  "Selects the specified knot, making it and its ancestors the active path."
  [{:keys [*session] :as request}]
  (let [id (knot-id request)]
    (swap! *session session/select-knot id)
    (render-app request {:scroll-to-knot-id id})))

(defn prepare-new-child
  "Prepares for adding a new child to the specified knot.
   Replays to the knot and deselects its children."
  [{:keys [*session] :as request}]
  (swap! *session session/prepare-new-child! (knot-id request))
  (render-app request {:scroll-to-new-command? true
                       :reset-command-input? true}))

(defn- render-edit-command-modal
  "Renders the edit command modal with optional error message."
  [id command error]
  {:status 200
   :body (html
          [modal/modal
           (cond-> {:title "Edit Command"
                    :signals {:editCommand command}
                    :content
                    [:form {:data-on:submit__stop (str "@post('/action/edit-command/" id "')")}
                     [:div.mb-4
                      [:label.block.text-sm.font-medium.text-gray-700.mb-2 {:for "edit-command-input"}
                       "Command:"]
                      [:input#edit-command-input
                       {:type "text"
                        :data-bind "editCommand"
                        :data-init "el.select()"
                        :class "w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"}]]
                     [:div.flex.justify-end.gap-2
                      [modal/cancel-button {}]
                      [modal/ok-button {}]]]}
             error (assoc :error error))])})

(defn open-edit-command
  "Opens the edit command modal for the specified knot."
  [{:keys [*session] :as request}]
  (let [id (knot-id request)
        tree (:tree @*session)
        knot (tree/get-knot tree id)
        command (:command knot)]
    (render-edit-command-modal id command nil)))

(defn edit-command
  "Submits the edited command for the knot and re-renders the app."
  [{:keys [*session signals] :as request}]
  (let [id (knot-id request)
        {:keys [editCommand]} signals
        command (some-> editCommand str string/trim not-empty)
        session' (when command
                   (swap! *session session/edit-command! id command))]
    (if-let [error (:error session')]
      ;; Error occurred - redisplay modal with error
      (render-edit-command-modal id command error)
      ;; Success - return both updated app and cleared modal
      {:status 200
       :body (html [:<>
                    (ui.app/render-app request {})
                    [:div#modal-container]])})))

(defn- render-insert-parent-modal
  "Renders the insert parent modal with optional error message."
  [id command error]
  {:status 200
   :body (html
          [modal/modal
           (cond-> {:title "Insert Parent"
                    :signals {:insertCommand command}
                    :content
                    [:form {:data-on:submit__stop (str "@post('/action/insert-parent/" id "')")}
                     [:div.mb-4
                      [:label.block.text-sm.font-medium.text-gray-700.mb-2 {:for "insert-parent-input"}
                       "Command:"]
                      [:input#insert-parent-input
                       {:type "text"
                        :data-bind "insertCommand"
                        :data-init "el.select()"
                        :class "w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"}]]
                     [:div.flex.justify-end.gap-2
                      [modal/cancel-button {}]
                      [modal/ok-button {}]]]}
             error (assoc :error error))])})

(defn open-insert-parent
  "Opens the insert parent modal for the specified knot."
  [{:keys [*session] :as request}]
  (let [id (knot-id request)]
    (render-insert-parent-modal id "" nil)))

(defn insert-parent
  "Submits the insert parent command for the knot and re-renders the app."
  [{:keys [*session signals] :as request}]
  (let [id (knot-id request)
        {:keys [insertCommand]} signals
        command (some-> insertCommand str string/trim not-empty)
        session' (when command
                   (swap! *session session/insert-parent! id command))]
    (if-let [error (:error session')]
      ;; Error occurred - redisplay modal with error
      (render-insert-parent-modal id command error)
      ;; Success - return both updated app and cleared modal
      {:status 200
       :body (html [:<>
                    (ui.app/render-app request {})
                    [:div#modal-container]])})))

(defn- render-edit-label-modal
  "Renders the edit label modal with optional error message."
  [id label error]
  {:status 200
   :body (html
          [modal/modal
           (cond-> {:title "Edit Label"
                    :signals {:editLabel label}
                    :content
                    [:form {:data-on:submit__stop (str "@post('/action/edit-label/" id "')")}
                     [:div.mb-4
                      [:label.block.text-sm.font-medium.text-gray-700.mb-2 {:for "edit-label-input"}
                       "Label:"]
                      [:input#edit-label-input
                       {:type "text"
                        :data-bind "editLabel"
                        :data-init "el.select()"
                        :class "w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"}]]
                     [:div.flex.justify-end.gap-2
                      [modal/cancel-button {}]
                      [modal/ok-button {}]]]}
             error (assoc :error error))])})

(defn open-edit-label
  "Opens the edit label modal for the specified knot."
  [{:keys [*session] :as request}]
  (let [id (knot-id request)
        tree (:tree @*session)
        knot (tree/get-knot tree id)
        label (or (:label knot) "")]
    (render-edit-label-modal id label nil)))

(defn edit-label
  "Submits the edited label for the knot and re-renders the app."
  [{:keys [*session signals] :as request}]
  (let [id (knot-id request)
        {:keys [editLabel]} signals
        label (some-> editLabel str string/trim)
        tree (:tree @*session)
        existing-knot (when-not (string/blank? label)
                        (tree/find-by-label tree label))
        is-duplicate? (and existing-knot (not= id (:id existing-knot)))]
    (if is-duplicate?
      ;; Duplicate found in a different knot - return modal with error
      (render-edit-label-modal id label (str "Label \"" label "\" is already used by another knot."))
      ;; No duplicate or same knot - proceed with update
      (do
        (swap! *session session/label id label)
        {:status 200
         :body (html [:<>
                      (ui.app/render-app request {})
                      [:div#modal-container]])}))))

(defn dismiss-modal
  "Dismisses any open modal by clearing the modal-container."
  [request]
  {:status 200
   :body (html [:div#modal-container])})

(defn undo
  "Undoes the last action by restoring the previous tree state."
  [{:keys [*session] :as request}]
  (swap! *session session/undo)
  (render-app request {:flash "Undo"}))

(defn redo
  "Redoes the last undone action by restoring the next tree state."
  [{:keys [*session] :as request}]
  (swap! *session session/redo)
  (render-app request {:flash "Redo"}))

(defn save
  "Saves the current tree state to the file."
  [{:keys [*session] :as request}]
  (swap! *session session/save!)
  (render-app request {:flash "Saved"}))

(defn replay-all
  "Replays to all leaf knots with SSE progress updates."
  [{:keys [*session] :as request}]
  (utils/start-sse
   request
   (fn [sse-gen]
     (let [initial-tree (:tree @*session)
           leaf-knots (tree/leaf-knots initial-tree)
           total (count leaf-knots)]
       ;; Capture initial state for undo (once for entire operation)
       (swap! *session session/capture-undo)

       ;; Replay to each leaf knot
       (doseq [[idx knot] (map-indexed vector leaf-knots)]
         (let [current (inc idx)
               {:keys [id label]} knot]
           ;; Update progress
           (utils/patch-elements!
            *session
            (html (progress/progress-modal
                   {:current current
                    :total total
                    :label label
                    :operation "Replaying All"})))

           ;; Replay to this leaf (using do-replay-to! to avoid capturing undo)
           (swap! *session #(session/do-replay-to! % id))))

       ;; Close progress modal and render final state
       (utils/patch-elements!
        *session
        (html [:<>
               (ui.app/render-app request {:flash "Replay complete"})
               [:div#modal-container]]))))))

(defn delete-knot
  "Deletes the specified knot and all its descendants."
  [{:keys [*session] :as request}]
  (swap! *session session/delete (knot-id request))
  (render-app request {:flash "Deleted"}))

(defn splice-out-knot
  "Splices out the specified knot, reparenting its children."
  [{:keys [*session] :as request}]
  (let [session' (swap! *session session/splice-out! (knot-id request))]
    (if-let [error (:error session')]
      ;; TODO: Display error to user properly
      (render-app request {:flash error})
      (render-app request {:flash "Spliced out"}))))
