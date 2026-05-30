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

(defn- spine-expanded-ids
  "Returns the expanded-ids set covering all ancestors of knot-id (inclusive),
  i.e. all knot IDs on the path from root to knot-id."
  [tree knot-id]
  (->> (tree/knots-from-root tree knot-id)
       (map :id)
       set))

(defn create-loaded!
  "Creates a new session from a tree loaded from the path, and a process started with
  the skein's seed."
  [start-process-fn skein-path tree]
  (let [;; The transcript displays tree/selected-knots (root → leaf via the :selected map).
        ;; Use that same path: the last knot is the active knot, and all knots on the
        ;; path become the initial expanded-ids for the nav graph.
        selected     (tree/selected-knots tree)
        leaf-knot-id (or (some-> selected last :id) 0)
        tree'        (assoc tree :active-knot-id leaf-knot-id)]
    {:skein-path       skein-path
     :undo-stack       []
     :redo-stack       []
     :start-process-fn start-process-fn
     :tree             tree'
     :process-knot-id  0
     :expanded-ids     (into #{} (map :id selected))}))

(defn create-new!
  "Creates a new session for a new skein, using an existing process.  The process should be
  just started, so that we can read the initial response. Creates a new tree for the session."
  [start-process-fn skein-path engine seed]
  (create-loaded! start-process-fn skein-path (tree/new-tree engine seed)))

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
        dynamic-response (when debug-enabled?
                           (sk.process/send-command! process "@dynamic"))]
    (cond-> session
      dynamic-response (update :tree tree/update-dynamic process-knot-id dynamic-response))))

(defn- run-command!
  [session command]
  ;; If there was a startup error, then don't try to execute further commands until
  ;; that's resolved.
  (if (:error session)
    session
    (let [{:keys [tree process-knot-id process]} session
          child-knot-id (tree/find-child-id tree process-knot-id command)
          response (sk.process/send-command! process command)
          session' (if child-knot-id
                     (-> session
                         (assoc :process-knot-id child-knot-id)
                         (update :tree tree/update-response child-knot-id response))
                     (let [new-knot-id (tree/next-id)]
                       (-> session
                           (assoc :process-knot-id new-knot-id)
                           (update :tree tree/add-child process-knot-id new-knot-id command response))))]
      (capture-dynamic session'))))

(defn- collect-commands
  [tree initial-knot-id]
  (->> (tree/knots-from-root tree initial-knot-id)
       (drop 1) ; no command for root knot
       (map :command)))

(defn- do-restart!
  "Kills the current process (if any) and starts a new one."
  [session]
  (let [{:keys [process start-process-fn]} session
        _ (sk.process/kill! process)
        process' (try
                   (start-process-fn)
                   (catch Exception e
                     {:startup-error (ex-message e)}))]
    (if-let [startup-error (:startup-error process')]
      (assoc session :process nil :error startup-error)
      ;; Read the initial startup text (or the startup error)
      (let [initial-response (sk.process/read-response! process')
            session' (assoc session :process process')]
        (if-not (sk.process/alive? process')
          (assoc session' :error initial-response)
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

(defn do-replay-to!
  "Replays to a knot without capturing undo. Used internally by replay-to!"
  [session knot-id]
  (let [commands (collect-commands (:tree session) knot-id)
        session' (reduce run-command! (do-restart! session) commands)]
    (if (:error session')
      session'
      (assoc session' :process-knot-id knot-id))))

(defn collect-replay-to
  "Replays to knot-id using a freshly spawned process, without touching the
  session's own process. Returns a map of knot-id -> [response dynamic] for every
  knot along the path from root to knot-id. dynamic is the raw @dynamic string,
  or nil if debug-enabled? is false.
  On process startup failure, returns {:error <message>}."
  [session knot-id]
  (let [knots (tree/knots-from-root (:tree session) knot-id)
        ;; Start a worker with a nil process so do-restart! won't kill the live one.
        worker (-> session
                   (assoc :process nil)
                   do-restart!)]
    (if (:error worker)
      {:error (:error worker)}
      (let [commands (->> knots (drop 1) (map :command))
            worker' (reduce run-command! worker commands)]
        (sk.process/kill! (:process worker'))
        (if (:error worker')
          {:error (:error worker')}
          (->> knots
               (map (fn [{:keys [id]}]
                      (let [knot (get-in worker' [:tree :knots id])]
                        ;; run-command! stores the fresh response in :unblessed when it
                        ;; differs from the blessed :response.  We want the actual new
                        ;; response, so prefer :unblessed, falling back to :response.
                        [id [(or (:unblessed knot) (:response knot))
                             (get-in worker' [:tree :dynamic id :response])]])))
               (into {})))))))

(defn apply-responses
  "Applies a collected result map (from collect-replay-to) to the session's tree.
  Each entry is knot-id -> [response dynamic]. Updates :response/:unblessed/:status/
  :descendant-status for each knot, and restores dynamic state when present."
  [session collected]
  (update session :tree
          (fn [tree]
            (reduce (fn [t [knot-id [response dynamic]]]
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
  current spine (root → selected leaf via :selected links, including any
  single-child extension from extend-selection) so the nav graph shows the
  full path."
  [session knot-id]
  (-> session
      (assoc-in [:tree :active-knot-id] knot-id)
      (update :expanded-ids (fnil into #{}) (map :id (tree/selected-knots (:tree session))))))

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
        session' (if (= process-knot-id parent-knot-id)
                   session
                   (do-replay-to! session parent-knot-id))
        session'' (-> session'
                      capture-undo
                      (run-command! command))]
    ;; Make the newly added/updated child knot the active (highlighted) knot
    (set-active-knot-id session'' (:process-knot-id session''))))

(defn replay-to!
  "Restarts the game, then plays through all the commands leading up to the knot.
  This will either verify that each knot's response is unchanged, or capture
  unblessed responses to be verified.
  
  Alternately, there may be a startup error, in which case the
  returned session will have an :error key."
  [session knot-id]
  (do-replay-to! (capture-undo session) knot-id))

(defn prepare-new-child!
  "Prepares the session to add a new child to the specified knot.
   Selects the knot and clears its :selected so it becomes the leaf of the selected path.
   The actual replay to that knot happens in command! when the user types a command."
  [session knot-id]
  (-> session
      capture-undo
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
             capture-undo
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
        (let [new-id (tree/next-id)
              session' (-> session
                           capture-undo
                           (update :tree tree/insert-parent knot-id new-id new-command)
                           (do-replay-to! knot-id))]
          [nil (cond-> session'
                 (not (:error session')) (assoc :new-id new-id))])))))

(defn totals
  "Totals the number of nodes that are :ok, :new, or :error."
  [session]
  (->> session
       :tree
       tree/totals))

(defn bless-knot
  "Blesses only the single knot, leaving all other knots unchanged."
  [session knot-id]
  (-> session
      capture-undo
      (update :tree tree/bless-response knot-id)))

(defn bless-to
  "Blesses all knots from root to knot-id."
  [session knot-id]
  (let [{:keys [tree]} session
        ids (->> (tree/knots-from-root tree knot-id)
                 (map :id))]
    (-> session
        capture-undo
        (assoc :tree (reduce tree/bless-response tree ids)))))

(defn select-knot
  "Selects a knot as the last knot displayed. Updates the spine from root to
  knot-id, then extends selection downward through single-child chains until a
  leaf or branch point, so the transcript shows the full unambiguous path."
  [session knot-id]
  (-> session
      capture-undo
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
   Captures undo so the reload can be undone."
  [session]
  (-> session
      capture-undo
      (assoc :tree (sk.file/load-tree (:skein-path session))
             :dirty? false)))

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
      capture-undo
      (update :tree tree/label-knot knot-id label)
      (update :tree tree/set-locked knot-id locked?)))

(defn toggle-lock
  "Toggles the locked state of a knot. Creates one undo entry."
  [session knot-id]
  (let [locked? (tree/locked? (:tree session) knot-id)]
    (-> session
        capture-undo
        (update :tree tree/set-locked knot-id (not locked?)))))

(defn delete!
  "Deletes a knot from the tree, including any descendants of the knot.
  Returns a tuple of [error session]. If a locked knot exists in the subtree,
  returns an error message and the unchanged session.
  When the active knot is deleted (or is a descendant of the deleted knot),
  the active knot is moved to the deleted knot's parent."
  [session knot-id]
  (let [tree (:tree session)
        active-knot-id (:active-knot-id tree)]
    (if-not (tree/allow-deletion? tree knot-id)
      ["Cannot delete: this knot (or a descendant) is locked." session]
      (let [parent-id (-> (tree/get-knot tree knot-id) :parent-id)
            deleted-ids (set (map :id (tree/knots-from-root tree knot-id)))
            active-knot-deleted? (contains? deleted-ids active-knot-id)
            process-knot-deleted? (contains? deleted-ids (:process-knot-id session))]
        [nil (cond-> (-> session
                         capture-undo
                         (update :tree tree/delete-knot knot-id))
               active-knot-deleted? (set-active-knot-id parent-id)
               process-knot-deleted? (assoc :process-knot-id parent-id))]))))

(defn q [s] (str \' s \'))

(defn splice-out!
  "Deletes a knot, reparenting its children to the knot's parent (unless there
  are command conflicts).
  
  Returns a tuple of error and the new session."
  [session knot-id]
  (let [tree (:tree session)
        active-knot-id (:active-knot-id tree)
        {:keys [parent-id children]} (tree/get-knot tree knot-id)
        parent-commands (->> (tree/find-children tree parent-id)
                             (remove #(= knot-id (:id %)))
                             (map :command)
                             set)
        child-commands (->> (tree/find-children tree knot-id)
                            (map :command)
                            set)
        overlaps (set/intersection parent-commands child-commands)]
    (if (seq overlaps)
      [(format "Parent already has %s %s"
               (pluralize-noun (count overlaps) "command")
               (->> overlaps
                    sort
                    (map q)
                    h/oxford))
       session]
      (let [replay-id (first children)
            active-is-spliced? (= active-knot-id knot-id)]
        [nil (cond-> (-> session
                         capture-undo
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
      (let [process (:process session')
            ;; Enable tracing, execute command, disable tracing
            _ (sk.process/send-command! process "(trace on)")
            trace-response (sk.process/send-command! process command)
            _ (sk.process/send-command! process "(trace off)")
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
        _ (sk.process/kill! process)
        traced-process (try
                         (start-process-fn {:extra-arguments ["--trace"]})
                         (catch Exception e
                           {:startup-error (ex-message e)}))]
    (if-let [startup-error (:startup-error traced-process)]
      [nil (assoc session :process nil :error startup-error)]
      (let [trace-response (sk.process/read-response! traced-process)]
        ;; Kill the traced process and restart normally
        (sk.process/kill! traced-process)
        (let [session' (do-restart! session)]
          (if (:error session')
            [nil session']
            [trace-response session']))))))

(defn get-knot
  [session id]
  (tree/get-knot (:tree session) id))

(defn selected-knots
  [session]
  (tree/selected-knots (:tree session)))

(defn toggle-expanded
  "Toggles the expanded state of a knot in the tree pane.
  Expanded knot IDs are stored in :expanded-ids on the session (not persisted)."
  [session knot-id]
  (if (contains? (:expanded-ids session) knot-id)
    (update session :expanded-ids disj knot-id)
    (update session :expanded-ids (fnil conj #{}) knot-id)))
