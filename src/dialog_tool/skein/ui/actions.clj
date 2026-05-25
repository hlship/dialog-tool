(ns dialog-tool.skein.ui.actions
  (:require [dialog-tool.env :as env]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.trace :as trace]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.ui.common :as common]
            [dialog-tool.skein.ui.js :as js]
            [dialog-tool.skein.ui.modals :as modals]
            [hyper.core :as h]))

;; TODO: These may belong elsewhere

(defn session-cursor
  []
  (h/global-cursor :session))

(defn modal-cursor
  []
  (h/global-cursor :modal))

(defn init-modal
  [type & kvs]
  (reset! (modal-cursor)
          (apply hash-map :type type kvs)))

;; Pending flash message — stored outside the session cursor so it doesn't
;; persist across re-renders. The render function consumes and clears it.
;; TODO: Never happy to have a global like this, there must be a better way
;; to deal with this.
(def *pending-flash (atom nil))

(defn flash!
  "Queues a flash message to be shown on the next render.
  Does not modify the session cursor — the render function reads and clears this."
  [message]
  (reset! *pending-flash message))

(defn complete-session-operation
  "Invoked after a command that operates on the process; if there was a startup error
  after restarting the process (because of a source code error) then sets up the source error
  modal dialog.
  
  If there's no error, updates the flash message."
  [session flash-message]
  (let [{:keys [error]} session]
    (when-not error
      (reset! *pending-flash flash-message))
    (cond-> session
      error (common/setup-source-error error))))

(defn replay-all
  "Replays all leaf knots. 
  The session is accumulated locally and only committed at the end."
  []
  (let [*session   (session-cursor)
        *modal     (modal-cursor)
        session    (-> @*session
                       session/capture-undo
                       session/check-for-changed-sources)
        leaf-knots (tree/leaf-knots (:tree session))
        total      (count leaf-knots)
        cancelled? #(not (:continue @*modal))]
    ;; Store continue flag in progress so cancel can stop the loop
    (env/log-action "replay-all")
    (init-modal :progress
                :operation "Replaying All"
                :continue true)
    (let [session'      (reduce (fn [session [idx knot]]
                                  (if (or (cancelled?)
                                          (:error session))
                                    (reduced session)
                                    (do
                                      (swap! *modal assoc
                                             :current (inc idx)
                                             :total total
                                             :label (:label knot))
                                      (session/do-replay-to! session (:id knot)))))
                                session
                                (map-indexed vector leaf-knots))
          was-canceled? (cancelled?)]
      ;; Clear the progress modal
      (modals/dismiss-modal *modal)
      (reset! *session
              (-> session'
                  ;; This will open the source error modal if necessary
                  (complete-session-operation (cond
                                                was-canceled? "Replay cancelled"
                                                ;; :loading? is just for a new skein, this ensures
                                                ;; that the process is launched and root node
                                                ;; collected.
                                                (:loading? session') nil
                                                :else "Replay complete"))
                  (dissoc :loading?))))))

(defn replay-to
  "Replays to a specific knot."
  [knot-id]
  (let [*session (h/global-cursor :session)]
    (env/log-action "replay-to" knot-id)
    (swap! *session
           #(-> %
                session/check-for-changed-sources
                (session/replay-to! knot-id)
                (complete-session-operation "Replayed")))))

(defn trace
  []
  (let [*session (h/global-cursor :session)]
    (swap! *session session/check-for-changed-sources)
    ;; Read active-knot-id from the live cursor here rather than
    ;; relying on the render-time captures of `root?` and `id`,
    ;; which may be stale if the button wasn't re-rendered after
    ;; the user selected a different knot.
    (let [session      @*session
          tree         (:tree session)
          active-id    (:active-knot-id tree)
          knot         (tree/get-knot tree active-id)
          root-action? (= 0 active-id)
          _            (env/log-action (if root-action? "trace-startup" "trace") active-id)
          command      (if root-action?
                         "Startup"
                         (:command knot))
          [trace-response session']
          ;; TODO: Handling source code error here?
          (if root-action?
            (session/trace-startup! session)
            (session/trace-command! session active-id))]
      ;; TODO: If a source error in trace, then jump directly to source error modal.
      #_(env/log-action "trace-done"
                        " response=" (if trace-response "present" "nil")
                        " error=" (if (:error session') (pr-str (:error session')) "none"))
      ;; Strip :modal and :trace before reset so Hyper always sees a
      ;; state change when the new trace is applied — even if the
      ;; content is identical to the previous run.
      (reset! *session session')
      (if trace-response
        (let [_      (env/log-action "trace-parse-start")
              parsed (trace/parse-trace trace-response)
              _      (env/log-action "trace-build-start" " lines=" (count parsed))
              nodes  (trace/build-tree parsed)
              _      (env/log-action "trace-swap-start" " nodes=" (trace/count-nodes nodes))]
          (init-modal :trace
                      :trace-state {:nodes      nodes
                                    :search     ""
                                    :node-count (trace/count-nodes nodes)
                                    :command    command}))
        (swap! *session common/maybe-apply-source-error)))))

(defn dynamic-state
  "Presents the raw dynamic response from the @dynamic command with minimal formatting."
  []
  (let [*session (h/global-cursor :session)
        session  @*session
        id       (get-in session [:tree :active-knot-id])
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

(defn bless
  [id]
  (env/log-action "bless" id)
  (swap! (session-cursor) session/bless id)
  (flash! "Blessed"))

(defn bless-to-here
  [id]
  (env/log-action "bless-to" id)
  (swap! (session-cursor) session/bless-to id)
  (flash! "Blessed to here"))

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
  (let [*session (session-cursor)]
    (swap! *session session/set-active-knot id)
    (js/scroll-knot-into-view! id)
    (js/focus-if-leaf! *session id)))

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
                             (session/set-active-knot next-id)))))))

(defn jump-to-label
  [id]
  (env/log-action "jump-to-label" id)
  (swap! (session-cursor)
         #(-> %
              (session/select-knot id)
              (session/set-active-knot id)
              (js/focus-if-leaf! id))))

(defn quit
  [*app-state]
  (env/log-action "quit")
  (let [*session (session-cursor)]
    (if (:dirty? @*session)
      (swap! *session assoc :modal {:type :quit})
      (do
        ;; Set closing state so the page renders the close message
        (swap! *session assoc :closing? true)
        (future
          ;; Give hyper time to push the closing message to the browser
          (Thread/sleep 500)
          ((get-in @*app-state [:global :shutdown-fn])))))))
