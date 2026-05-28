(ns dialog-tool.skein.ui.actions
  (:require [clojure.string :as string]
            [dialog-tool.env :as env]
            [dialog-tool.skein.search :as search]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.trace :as trace]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.ui.common :as common :refer [session-cursor modal-cursor search-cursor]]
            [dialog-tool.skein.ui.js :as js]
            [dialog-tool.skein.ui.modals :as modals]
            [hyper.core :as h]
            [hyper.effects :as effects]))

(defn init-modal
  [type & kvs]
  (reset! (modal-cursor)
          (apply hash-map :type type kvs)))


(defn flash!
  "Sends JS to the client to display a flash message.  This may be a string, or a map
  w/ keys :message and :type."
  [flash-message]
  (when flash-message
    (let [{:keys [message type]} (if (string? flash-message)
                                   {:message flash-message :type :info}
                                   flash-message)]
      (effects/execute-script!
        (str "sk.showFlash(" (pr-str message) "," (-> type name pr-str) ")")))))

(defn complete-session-operation
  "Invoked after a command that operates on the process; if there was a startup error
  after restarting the process (because of a source code error) then sets up the source error
  modal dialog.
  
  If there's no error, updates the flash message."
  [session flash-message]
  (let [{:keys [error]} session]
    (when-not error
      (flash! flash-message))
    (cond-> session
      error (common/setup-source-error error))))

(defn replay-all
  "Replays all leaf knots in parallel via pmap, then merges the collected
  responses into the session tree in a single pass."
  []
  (let [*session   (session-cursor)
        *modal     (modal-cursor)
        session    (-> @*session
                       session/capture-undo
                       session/check-for-changed-sources)
        leaf-knots (tree/leaf-knots (:tree session))
        total      (count leaf-knots)]
    (env/log-action "replay-all")
    (init-modal :progress
                :operation "Replaying All"
                :current 0
                :total total)
    (let [;; pmap runs collect-replay-to concurrently; each call spawns its own
          ;; process and is fully independent of the live session process.
          results     (pmap (fn [knot]
                              (let [result (session/collect-replay-to session (:id knot))]
                                (swap! *modal #(-> %
                                                   (update :current inc)
                                                   (assoc :label (:label knot))))
                                result))
                            leaf-knots)
          first-error (some :error results)
          session'    (if first-error
                        (assoc session :error first-error)
                        (session/apply-responses session (reduce merge {} results)))]
      (modals/dismiss-modal)
      (reset! *session
              (-> session'
                  (complete-session-operation (cond
                                                first-error nil
                                                (:loading? session') nil
                                                :else "Replay complete"))
                  (dissoc :loading?))))))

(defn replay-to
  "Replays to a specific knot."
  [knot-id]
  (let [*session (session-cursor)]
    (env/log-action "replay-to" knot-id)
    (swap! *session
           #(-> %
                session/check-for-changed-sources
                (session/replay-to! knot-id)
                (complete-session-operation "Replayed")))))

(defn initial-load
  []
  (env/log-action "initial-load")
  (let [*session (session-cursor)
        {:keys [loading?]} @*session]
    ;; loading? is true on startup for a new tree (not one loaded from an existing skein).
    (if loading?
      (do
        (swap! *session #(-> %
                             (session/replay-to! 0)
                             (complete-session-operation nil)
                             ;; Clear the spin cursor
                             (dissoc :loading?)
                             (js/navigate-to-knot! 0))))
      (replay-all))))

(defn trace
  []
  (let [*session (session-cursor)]
    (swap! *session session/check-for-changed-sources)
    ;; Read active-knot-id from the live cursor here rather than
    ;; relying on the render-time captures of `root?` and `id`,
    ;; which may be stale if the button wasn't re-rendered after
    ;; the user selected a different knot.
    (let [session      @*session
          active-id    (session/get-active-knot-id session)
          knot         (session/get-knot session active-id)
          root-action? (= 0 active-id)
          _            (env/log-action "trace" active-id)
          command      (if root-action?
                         "Startup"
                         (:command knot))
          [trace-response session'] (if root-action?
                                      (session/trace-startup! session)
                                      (session/trace-command! session active-id))]
      (reset! *session session')
      (if trace-response
        (let [parsed (trace/parse-trace trace-response)
              nodes  (trace/build-tree parsed)]
          (init-modal :trace
                      :nodes nodes
                      :search ""
                      :node-count (trace/count-nodes nodes)
                      :command command))
        (swap! *session common/maybe-apply-source-error)))))

(defn dynamic-state
  "Presents the raw dynamic response from the @dynamic command with minimal formatting."
  []
  (let [*session (session-cursor)
        session  @*session
        id       (session/get-active-knot-id session)
        {:keys [dynamic-response]} (session/get-knot session id)]
    (env/log-action "dynamic-state" id)
    (init-modal :dynamic-state :dynamic-response dynamic-response)))

(defn edit-command
  [id]
  (env/log-action "edit-command" id)
  (let [{:keys [command]} (session/get-knot @(session-cursor) id)]
    (init-modal :edit-command
                :id id
                :command command)))

(defn bless-knot
  [id]
  (env/log-action "bless-knot" id)
  (swap! (session-cursor) session/bless-knot id)
  (flash! "Blessed"))

(defn bless-changes
  [id]
  (env/log-action "bless-changes" id)
  (swap! (session-cursor) session/bless-to id)
  (flash! "Blessed"))

(defn new-child
  [id]
  (env/log-action "new-child" id)
  (swap! (session-cursor) session/prepare-new-child! id)
  (js/reset-and-focus-command-input!))

(defn edit-label
  [id]
  (env/log-action "edit-label" id)
  (let [{:keys [locked? label]} (session/get-knot @(session-cursor) id)]
    (init-modal :edit-label
                :id id
                :label label
                ;; default locked to true when creating
                ;; a label initially
                :locked (if (some? locked?)
                          locked?
                          true))))

(defn toggle-lock
  [id]
  (env/log-action "toggle-lock" id)
  (let [*session (session-cursor)
        locked?  (get-in @*session [:tree :knots id :locked])]
    (swap! *session session/toggle-lock id)
    (flash! (if locked? "Locked" "Unlocked"))))

(defn insert-parent
  [id]
  (env/log-action "insert-parent" id)
  (init-modal :insert-parent :id id))

(defn delete-knot
  [id]
  (env/log-action "delete" id)
  (let [*session (session-cursor)
        [error session'] (session/delete! @*session id)]
    (reset! *session session')
    (flash! (if error {:message error
                       :type    :error}
                      "Deleted"))))

(defn split-out
  [id]
  (env/log-action "splice-out" id)
  (let [*session (session-cursor)
        [error session'] (session/splice-out! @*session id)]
    (reset! *session session')
    (flash! (if error {:message error
                       :type    :error}
                      "Spliced Out"))))

(defn activate-knot
  [id]
  (env/log-action "activate-knot" id)
  (swap! (session-cursor) #(-> %
                               (session/set-active-knot-id id)
                               js/navigate-to-active-knot!)))

(defn seek-status
  [status]
  (env/log-action (str "seek-" (name status)))
  (let [*session (session-cursor)
        session  @*session
        tree     (:tree session)
        matching (tree/knots-with-status tree status)]
    (when (seq matching)
      (let [last-id  (get-in session [:last-jump status])
            idx      (when last-id
                       (let [i (.indexOf matching last-id)]
                         (when (>= i 0) i)))
            next-idx (if idx
                       (mod (inc idx) (count matching))
                       0)
            next-id  (nth matching next-idx)]
        (swap! *session #(-> %
                             (assoc-in [:last-jump status] next-id)
                             (session/select-knot next-id)
                             (session/set-active-knot-id next-id)))))))

(defn jump-to-label
  [id]
  (env/log-action "jump-to-label" id)
  (swap! (session-cursor)
         #(-> %
              (session/select-knot id)
              (session/set-active-knot-id id)
              js/navigate-to-active-knot!)))

(defn quit
  []
  (env/log-action "quit")
  (let [*session (session-cursor)]
    (if (:dirty? @*session)
      (init-modal :quit)
      (common/quit))))

(defn search
  [q]
  (let [q'           (-> q str string/trim)
        *search      (search-cursor)
        *tree-cursor (h/global-cursor [:session :tree])]
    (env/log-action "search" (pr-str q'))
    (if (string/blank? q')
      (reset! *search nil)
      (swap! *search assoc
             :query q'
             :results (search/search-knots @*tree-cursor q' 50)))))

(defn dismiss-search
  "Clears the search results from the session and resets the search input."
  []
  (reset! (search-cursor) nil)
  (effects/execute-script! "document.getElementById('search-input').value = ''"))

(defn jump-to-search-selection
  [knot-id]
  (env/log-action "jump-to-search-selection" knot-id)
  (swap! (session-cursor) #(-> %
                               (session/select-knot knot-id)
                               (session/set-active-knot-id knot-id)))
  (reset! (search-cursor) nil)
  (swap! (session-cursor) js/navigate-to-knot! knot-id))
