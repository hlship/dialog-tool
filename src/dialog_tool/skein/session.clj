(ns dialog-tool.skein.session
  "A session wraps around a Skein process, maintaining a tree, the process, and the current knot.
  In addition, there's an undo and redo stack, representing prior states of the tree."
  (:require [clj-commons.humanize :as h]
            [clojure.set :as set]
            [clj-commons.humanize.inflect :refer [pluralize-noun]]
            [clojure.string :as string]
            [dialog-tool.skein.file :as sk.file]
            [dialog-tool.skein.process :as sk.process]
            [dialog-tool.skein.tree :as tree]))

(defn- init-tree
  "Sets :active-knot-id to the leaf of the selected spine and expands all nodes
  along that spine in :expanded-ids. Used when first loading or reloading a tree."
  [tree]
  (let [selected     (tree/selected-knots tree)
        leaf-knot-id (or (some-> selected last :id) 0)]
    (-> tree
        (assoc :active-knot-id leaf-knot-id)
        (tree/expand-ids (map :id selected)))))

(defn create-loaded!
  "Creates a new session from a tree loaded from the path, and a process started with
  the skein's seed."
  [start-process-fn skein-path tree]
  {:skein-path       skein-path
   :undo-stack       []
   :redo-stack       []
   :start-process-fn start-process-fn
   :tree             (init-tree tree)
   :process-knot-id  0})

(defn create-new!
  "Creates a new session for a new skein, using an existing process.  The process should be
  just started, so that we can read the initial response. Creates a new tree for the session."
  [start-process-fn skein-path engine seed]
  (create-loaded! start-process-fn skein-path (tree/new-tree engine seed)))

(defn get-knot
  [session id]
  (tree/get-knot (:tree session) id))

(defn capture-undo
  [session]
  (-> session
      (update :undo-stack conj (:tree session))
      ;; TODO: Really shouldn't clear it unless :tree changed at end; macro time?
      (update :redo-stack empty)
      (assoc :dirty? true)))

(defn- capture-dynamic
  [session]
  (let [{:keys [process-knot-id debug-enabled? process]} session
        {:keys [response]} (when debug-enabled?
                             (sk.process/send-command! process "@dynamic"))]
    (cond-> session
      response (update :tree tree/update-dynamic process-knot-id response))))

(def ^:private command->key
  {"enter"     "\n"
   "space"     " "
   "backspace" "\b"})

(defn- run-command!
  [session command]
  ;; If there was a startup error, then don't try to execute further commands until
  ;; that's resolved.
  (if (:error session)
    session
    (let [{:keys [tree process-knot-id process]} session
          active-prompt (-> tree
                            (tree/get-knot process-knot-id)
                            :prompt)
          keystroke?     (= active-prompt :keystroke)
          child-knot-id (tree/find-child-id tree process-knot-id command)
          command'      (if-not keystroke?
                          command
                          (get command->key command command))
          {:keys [prompt] :as response} (sk.process/send-command! process command' keystroke?)
          session'      (if child-knot-id
                          (-> session
                              (assoc :process-knot-id child-knot-id)
                              (update :tree tree/update-response child-knot-id response))
                          (let [new-knot-id (tree/next-id)]
                            (-> session
                                (assoc :process-knot-id new-knot-id)
                                (update :tree tree/add-child process-knot-id new-knot-id command response))))]
      (if (not= prompt :keystroke)
        (capture-dynamic session')
        session'))))

(defn- collect-commands
  [tree initial-knot-id]
  (->> (tree/knots-from-root tree initial-knot-id)
       (drop 1)                                             ; no command for root knot
       (map :command)))

(defn- do-restart!
  "Kills the current process (if any) and starts a new one."
  [session]
  (let [{:keys [process start-process-fn]} session
        _        (sk.process/kill! process)
        process' (try
                   (start-process-fn)
                   (catch Exception e
                     {:startup-error (ex-message e)}))]
    (if-let [startup-error (:startup-error process')]
      (assoc session :process nil :error startup-error)
      ;; Read the initial startup text (or the startup error)
      (let [initial-response (sk.process/read-response! process')
            session'         (assoc session :process process')]
        (if-not (sk.process/alive? process')
          (assoc session' :error (:response initial-response))
          (-> session'
              (assoc :process-knot-id 0)
              (dissoc :error)
              (update :tree tree/update-response 0 initial-response)
              capture-dynamic))))))

(defn check-for-changed-sources
  [session]
  (if (-> session :process sk.process/sources-changed?)
    (assoc session :process-knot-id nil)
    session))

(defn- run-commands!
  [session commands]
  (loop [session  (do-restart! session)
         commands (seq commands)]
    (reduce run-command! (do-restart! session) commands)))

(defn do-replay-to!
  "Replays to a knot without capturing undo. Used internally by replay-to!"
  [session knot-id]
  (let [commands (collect-commands (:tree session) knot-id)
        session' (run-commands! session commands)]
    (if (:error session')
      session'
      (assoc session' :process-knot-id knot-id))))

(defn collect-replay-to
  "Replays to knot-id using a freshly spawned process, without touching the
  session's own process. Returns a map of knot-id -> [response prompt dynamic] for every
  knot along the path from root to knot-id. dynamic is the raw @dynamic string,
  or nil if debug-enabled? is false.
  On process startup failure, returns {:error <message>}."
  [session knot-id]
  (let [knots  (tree/knots-from-root (:tree session) knot-id)
        ;; Start a worker with a nil process so do-restart! won't kill the live one.
        worker (assoc session :process nil)]
    (if (:error worker)
      {:error (:error worker)}
      (let [commands (->> knots (drop 1) (map :command))
            worker'  (run-commands! worker commands)]
        (sk.process/kill! (:process worker'))
        (if (:error worker')
          {:error (:error worker')}
          (->> knots
               (map (fn [{:keys [id]}]
                      (let [{:keys [prompt unblessed response]} (get-in worker' [:tree :knots id])]
                        ;; run-command! stores the fresh response in :unblessed when it
                        ;; differs from the blessed :response.  We want the actual new
                        ;; response, so prefer :unblessed, falling back to :response.
                        [id [{:response (or unblessed response)
                              :prompt prompt}
                             prompt
                             (get-in worker' [:tree :dynamic id :response])]])))
               (into {})))))))

(defn apply-responses
  "Applies a collected result map (from collect-replay-to) to the session's tree.
  Each entry is knot-id -> [response prompt dynamic]. Updates :response/:unblessed/:status/
  :descendant-status for each knot, and restores dynamic state when present."
  [session collected]
  (update session :tree
          (fn [tree]
            (reduce (fn [t [knot-id [response _prompt dynamic]]]
                      (-> t
                          (tree/update-response knot-id response)
                          (cond-> dynamic (tree/update-dynamic knot-id dynamic))))
                    tree
                    collected))))

(defn get-active-knot-id
  [session]
  (get-in session [:tree :active-knot-id]))

(defn set-active-knot-id
  "Sets the active knot id without capturing undo. Expands all nodes on the
  current spine in tree[:expanded-ids] so the nav graph shows the full path."
  [session knot-id]
  (-> session
      (assoc-in [:tree :active-knot-id] knot-id)
      (update :tree tree/expand-ids (map :id (tree/selected-knots (:tree session))))))

(defn command!
  "Sends a player command to the process as a child of the given parent knot.
  This will either update a child of the parent knot (with a possibly unblessed
  response) or will create a new child knot.
  
  It is possible that the process may fail at startup due to invalid code,
  in which case the session will include an :error key.

  Returns the updated session."
  [session parent-knot-id command]
  (let [{:keys [process-knot-id]} session
        ;; Replay if process position doesn't match where we want to execute the command
        replayed-session (if (= process-knot-id parent-knot-id)
                           session
                           (do-replay-to! session parent-knot-id))
        final-session    (run-command! replayed-session command )]
    ;; Make the newly added/updated child knot the active (highlighted) knot
    (set-active-knot-id final-session (:process-knot-id final-session))))

(defn replay-to!
  "Restarts the game, then plays through all the commands leading up to the knot.
  This will either verify that each knot's response is unchanged, or capture
  unblessed responses to be verified.
  
  Alternately, there may be a startup error, in which case the
  returned session will have an :error key."
  [session knot-id]
  (do-replay-to! session knot-id))

(defn prepare-new-child!
  "Prepares the session to add a new child to the specified knot.
   Selects the knot and clears its :selected so it becomes the leaf of the selected path.
   The actual replay to that knot happens in command! when the user types a command."
  [session knot-id]
  (-> session
      (update :tree tree/select-knot knot-id)
      (update :tree tree/deselect knot-id)
      (set-active-knot-id knot-id)))

(defn edit-command!
  "Returns a tuple of error and new session."
  [session knot-id new-command]
  (if (string/blank? new-command)
    [nil session]
    (let [{:keys [tree]} session
          {:keys [parent-id]} (tree/get-knot tree knot-id)
          existing-child (tree/find-child-id tree parent-id new-command)]
      (if existing-child
        [(format "Parent knot already contains a child with command '%s'"
                 new-command)
         session]
        [nil
         (-> session
             (update :tree tree/change-command knot-id new-command)
             (do-replay-to! knot-id))]))))

(defn insert-parent!
  [session knot-id new-command]
  (if (string/blank? new-command)
    [nil session]
    (let [{:keys [tree]} session
          {:keys [parent-id]} (tree/get-knot tree knot-id)
          existing-child (tree/find-child-id tree parent-id new-command)]
      (if existing-child
        [(format "Parent knot already contains a child with command '%s'"
                 new-command)
         session]
        (let [new-id   (tree/next-id)
              session' (-> session
                           (update :tree tree/insert-parent knot-id new-id new-command)
                           (do-replay-to! knot-id))]
          [nil (cond-> session'
                 (not (:error session'))
                 (-> (assoc :new-id new-id)
                     ;; Navigate to the new parent as part of the same undo entry.
                     ;; Use tree functions directly (select-knot is defined later in file).
                     (update :tree tree/select-knot new-id)
                     (update :tree tree/extend-selection new-id)
                     (set-active-knot-id new-id)))])))))

(defn totals
  "Totals the number of nodes that are :ok, :new, or :error."
  [session]
  (->> session
       :tree
       tree/totals))

(defn bless-knot
  "Blesses only the single knot, leaving all other knots unchanged."
  [session knot-id]
  (update session :tree tree/bless-response knot-id))

(defn bless-to
  "Blesses all knots from root to knot-id."
  [session knot-id]
  (let [{:keys [tree]} session
        ids (->> (tree/knots-from-root tree knot-id)
                 (map :id))]
    (assoc session :tree (reduce tree/bless-response tree ids))))

(defn select-knot
  "Updates the spine from root to knot-id, then extends selection downward through
  single-child chains until a leaf or branch point. Does not capture undo — callers
  are responsible for calling capture-undo before navigation if desired."
  [session knot-id]
  (-> session
      (update :tree tree/select-knot knot-id)
      (update :tree tree/extend-selection knot-id)))

(defn save!
  "Saves the current tree state to the file."
  [session]
  (let [{:keys [tree skein-path]} session]
    (sk.file/save-tree tree skein-path)
    (assoc session :dirty? false)))

(defn can-reload?
  "Returns true when the skein file exists on disk (i.e. has been saved at least once).
   New skeins have no file yet, so reload is disabled until after the first save."
  [session]
  (sk.file/exists? (:skein-path session)))

(defn reload!
  "Re-reads the skein file from disk, replacing the current tree.
  Restores active-knot-id and expanded-ids to match the selected spine,
  the same as when the skein is first opened."
  [session]
  (assoc session
         :tree (-> (:skein-path session) sk.file/load-tree init-tree)
         :dirty? false))

(defn undo
  "Undoes the state of the tree one step; the current tree is pushed onto the
  redo stack."
  [session]
  (let [tree' (-> session :undo-stack peek)]
    (-> session
        (assoc :tree tree')
        (update :redo-stack conj (:tree session))
        (update :undo-stack pop))))

(defn redo
  "Reverts an undo, restoring a prior state of the tree."
  [session]
  (let [tree' (-> session :redo-stack peek)]
    (-> session
        (assoc :tree tree')
        (update :undo-stack conj (:tree session))
        (update :redo-stack pop))))

(defn label
  [session knot-id label locked?]
  (-> session
      (update :tree tree/label-knot knot-id label)
      (update :tree tree/set-locked knot-id locked?)))

(defn toggle-lock
  "Toggles the locked state of a knot."
  [session knot-id]
  (let [locked? (tree/locked? (:tree session) knot-id)]
    (update session :tree tree/set-locked knot-id (not locked?))))

(defn delete!
  "Deletes a knot from the tree, including any descendants of the knot.
  Returns a tuple of [error session]. If a locked knot exists in the subtree,
  returns an error message and the unchanged session.
  When the active knot is deleted (or is a descendant of the deleted knot),
  the active knot is moved to the deleted knot's parent."
  [session knot-id]
  (let [tree           (:tree session)
        active-knot-id (:active-knot-id tree)]
    (if-not (tree/allow-deletion? tree knot-id)
      ["Cannot delete: this knot (or a descendant) is locked." session]
      (let [parent-id             (-> (tree/get-knot tree knot-id) :parent-id)
            deleted-ids           (set (map :id (tree/knots-from-root tree knot-id)))
            active-knot-deleted?  (contains? deleted-ids active-knot-id)
            process-knot-deleted? (contains? deleted-ids (:process-knot-id session))]
        [nil (cond-> (update session :tree tree/delete-knot knot-id)
               active-knot-deleted? (set-active-knot-id parent-id)
               process-knot-deleted? (assoc :process-knot-id parent-id))]))))

(defn q [s] (str \' s \'))

(defn splice-out!
  "Deletes a knot, reparenting its children to the knot's parent (unless there
  are command conflicts).
  
  Returns a tuple of error and the new session."
  [session knot-id]
  (let [tree            (:tree session)
        active-knot-id  (:active-knot-id tree)
        {:keys [parent-id children]} (tree/get-knot tree knot-id)
        parent-commands (->> (tree/find-children tree parent-id)
                             (remove #(= knot-id (:id %)))
                             (map :command)
                             set)
        child-commands  (->> (tree/find-children tree knot-id)
                             (map :command)
                             set)
        overlaps        (set/intersection parent-commands child-commands)]
    (if (seq overlaps)
      [(format "Parent already has %s %s"
               (pluralize-noun (count overlaps) "command")
               (->> overlaps
                    sort
                    (map q)
                    h/oxford))
       session]
      (let [replay-id          (first children)
            active-is-spliced? (= active-knot-id knot-id)]
        [nil (cond-> (-> session
                         (update :tree tree/splice-out knot-id)
                         (assoc :new-id (or replay-id parent-id)))
               ;; Replay to first child if exists (process state still valid for that path)
               replay-id (do-replay-to! replay-id)
               ;; If we spliced the active knot and no children, move active to parent
               (and active-is-spliced? (not replay-id)) (set-active-knot-id parent-id)
               ;; If the process was at the spliced knot and no children, reset it
               (and (= (:process-knot-id session) knot-id) (not replay-id))
               (assoc :process-knot-id parent-id))]))))

(defn trace-command!
  "Replays to the parent of the given knot, then executes the knot's command
   with tracing enabled. Returns [trace-response session'] where trace-response
   is the raw response containing trace lines. The tree's response for the knot
   is NOT updated (since it contains trace noise).

   If there is a startup error, returns [nil session'] where session' has :error set.
   
   Only works with the dgdebug engine."
  [session knot-id]
  (let [{:keys [tree]} session
        {:keys [command parent-id]} (tree/get-knot tree knot-id)
        ;; Replay to parent (may restart process)
        session' (do-replay-to! session parent-id)]
    (if (:error session')
      [nil session']
      (let [process   (:process session')
            ;; Enable tracing, execute command, disable tracing
            _         (sk.process/send-command! process "(trace on)")
            {trace-response :response} (sk.process/send-command! process command)
            _         (sk.process/send-command! process "(trace off)")
            ;; Update process position and capture dynamic state
            session'' (-> session'
                          (assoc :process-knot-id knot-id)
                          capture-dynamic)]
        [trace-response session'']))))

(defn trace-startup!
  "Traces game startup by restarting the process with the --trace flag.
   The traced startup response is captured, then the process is restarted
   normally (without --trace) to restore clean state.
   
   Returns [trace-response session'] like trace-command!.
   If there is a startup error, returns [nil session'] where session' has :error set."
  [session]
  (let [{:keys [process start-process-fn]} session
        ;; Start a temporary process with --trace to capture traced startup
        _              (sk.process/kill! process)
        traced-process (try
                         (start-process-fn {:extra-arguments ["--trace"]})
                         (catch Exception e
                           {:startup-error (ex-message e)}))]
    (if-let [startup-error (:startup-error traced-process)]
      [nil (assoc session :process nil :error startup-error)]
      (let [{trace-response :response} (sk.process/read-response! traced-process)]
        ;; Kill the traced process and restart normally
        (sk.process/kill! traced-process)
        (let [session' (do-restart! session)]
          (if (:error session')
            [nil session']
            [trace-response session']))))))


(defn selected-knots
  [session]
  (tree/selected-knots (:tree session)))

(defn toggle-expanded
  "Toggles the expanded state of a knot in the nav graph."
  [session knot-id]
  (update session :tree tree/toggle-expanded knot-id))
