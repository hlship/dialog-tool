(ns dialog-tool.skein.session
  "A session wraps around a Skein process, maintaining a tree, the process, and the current knot.
  In addition, there's an undo and redo stack, representing prior states of the tree."
  (:require [clj-commons.humanize :as h]
            [clojure.set :as set]
            [clj-commons.humanize.inflect :refer [pluralize-noun]]
            [dialog-tool.skein.file :as sk.file]
            [dialog-tool.skein.process :as sk.process]
            [dialog-tool.skein.tree :as tree]))

(defn create-loaded!
  "Creates a new session from a tree loaded from the path, and a process started with
  the skein's seed. The process should be just started so that we can read the initial
  response."
  [process skein-path tree]
  (let [initial-response (sk.process/read-response! process)]
    {:skein-path skein-path
     :undo-stack []
     :redo-stack []
     :process process
     :tree (tree/update-response tree 0 initial-response)
     :active-knot-id 0}))

(defn create-new!
  "Creates a new session for a new skein, using an existing process.  The process should be
  just started, so that we can read the initial response. Creates a new tree for the session."
  [process skein-path engine seed]
  (create-loaded! process skein-path (tree/new-tree engine seed)))

(defn capture-undo
  [session]
  (-> session
      (update :undo-stack conj (:tree session))
      ;; TODO: Really shouldn't clear it unless :tree changed at end; macro time?
      (update :redo-stack empty)))

(defn- run-command!
  [session command]
  (let [{:keys [tree active-knot-id process]} session
        child-knot-id (tree/find-child-id tree active-knot-id command)
        response (sk.process/send-command! process command)]
    (if child-knot-id
      (-> session
          (assoc :active-knot-id child-knot-id)
          (update :tree tree/update-response child-knot-id response))
      (let [new-knot-id (tree/next-id)]
        (-> session
            (assoc :active-knot-id new-knot-id)
            (update :tree tree/add-child active-knot-id new-knot-id command response))))))

(defn- collect-commands
  [tree initial-knot-id]
  (->> (tree/knots-from-root tree initial-knot-id)
       (drop 1) ; no command for root knot
       (map :command)))

(defn- do-restart!
  [session]
  ;; An earlier version made use of '(restart)' but that had problems, and dgdebug is so fast
  ;; to start up that it doesn't make sense.
  (let [{:keys [process]} session
        process' (sk.process/restart! process)
        new-initial-response (sk.process/read-response! process')]
    (-> session
        (assoc :active-knot-id 0
               :process process')
        (update :tree tree/update-response 0 new-initial-response))))

(defn do-replay-to!
  "Replays to a knot without capturing undo. Used internally by replay-to! and for batch operations."
  [session knot-id]
  (let [commands (collect-commands (:tree session) knot-id)
        session' (reduce run-command! (do-restart! session) commands)]
    (assoc session' :active-knot-id knot-id)))

(defn command!
  "Sends a player command to the process at the current knot. This will either
  update a child of the current knot (with a possibly unblessed response) or
  will create a new child knot.

  Returns the updated session."
  [session command]
  (let [{:keys [tree active-knot-id]} session
        selected-leaf-id (-> (tree/selected-knots tree) last :id)
        ;; Replay if process position doesn't match where we want to execute the command
        session' (if (= active-knot-id selected-leaf-id)
                   session
                   (do-replay-to! session selected-leaf-id))]
    (-> session'
        capture-undo
        (run-command! command))))

(defn restart!
  "Restarts the game, sets the active knot to 0."
  [session]
  (-> session
      capture-undo
      do-restart!))

(defn replay-to!
  "Restarts the game, then plays through all the commands leading up to the knot.
  This will either verify that each knot's response is unchanged, or capture
  unblessed responses to be verified."
  [session knot-id]
  (do-replay-to! (capture-undo session) knot-id))

(defn prepare-new-child!
  "Prepares the session to add a new child to the specified knot.
   Selects the knot and clears its :selected so it becomes the leaf of the selected path.
   The actual replay will happen when the command is added."
  [session knot-id]
  (-> session
      capture-undo
      (update :tree tree/select-knot knot-id)
      (update :tree tree/deselect knot-id)
      (assoc :active-knot-id knot-id)))

(defn edit-command!
  [session knot-id new-command]
  (let [{:keys [tree]} session
        {:keys [parent-id]} (tree/get-knot tree knot-id)
        existing-child (tree/find-child-id tree parent-id new-command)]
    (if existing-child
      (assoc session :error
             (format "Parent knot already contains a child with command '%s'"
                     new-command))
      (-> session
          capture-undo
          (update :tree tree/change-command knot-id new-command)
          (do-replay-to! knot-id)))))

(defn insert-parent!
  [session knot-id new-command]
  (let [{:keys [tree]} session
        {:keys [parent-id]} (tree/get-knot tree knot-id)
        existing-child (tree/find-child-id tree parent-id new-command)]
    (if existing-child
      (assoc session :error
             (format "Parent knot already contains a child with command '%s'"
                     new-command))
      (let [new-id (tree/next-id)]
        (-> session
            capture-undo
            (update :tree tree/insert-parent knot-id new-id new-command)
            (do-replay-to! knot-id)
            (assoc :new-id new-id))))))

(defn- knot-category
  [{:keys [unblessed response]}]
  (cond
    (nil? unblessed) :ok

    (nil? response) :new

    (not= unblessed response) :error

    :else :ok))

(defn totals
  "Totals the number of nodes that are :ok, :new, or :error."
  [session]
  (->> session
       :tree
       tree/all-knots
       (reduce (fn [counts knot]
                 (update counts (knot-category knot) inc))
               {:ok 0 :new 0 :error 0})))

(defn bless
  [session knot-id]
  (-> session
      capture-undo
      (update :tree tree/bless-response knot-id)))

(defn bless-to
  [session knot-id]
  (let [{:keys [tree]} session
        ids (->> (tree/knots-from-root tree knot-id)
                 (map :id))]
    (-> session
        capture-undo
        (assoc :tree (reduce tree/bless-response tree ids)))))

(defn select-knot
  [session knot-id]
  (-> session
      capture-undo
      (cond-> (not= knot-id 0) (update :tree tree/select-knot knot-id))))

(defn save!
  "Saves the current tree state to the file.  Does not affect undo/redo."
  [session]
  (let [{:keys [tree skein-path]} session]
    (sk.file/save-tree tree skein-path)
    ;; If this moves around the undo/redo stack, may not get the right
    ;; behavior, maybe. Not sure.
    (assoc session :tree (tree/clean tree))))

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
  [session knot-id label]
  (-> session
      capture-undo
      (update :tree tree/label-knot knot-id label)))

(defn delete
  "Deletes a knot from the tree, including any descendants of the knot."
  [session knot-id]
  (let [{:keys [tree active-knot-id]} session
        ;; Check if active knot is the one being deleted or a descendant
        active-knot-deleted? (some #(= active-knot-id (:id %))
                                   (tree/knots-from-root tree knot-id))]
    (cond-> (-> session
                capture-undo
                (update :tree tree/delete-knot knot-id))
      ;; If we deleted the active knot, clear it so we'll replay on next command
      active-knot-deleted?
      (dissoc :active-knot-id))))

(defn q [s] (str \' s \'))

(defn splice-out!
  "Deletes a knot, reparenting its children to the knot's parent (unless there
  are command conflicts)."
  [session knot-id]
  (let [{:keys [tree active-knot-id]} session
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
      (assoc session :error
             (format "Parent already has %s %s"
                     (pluralize-noun (count overlaps) "command")
                     (->> overlaps
                          sort
                          (map q)
                          h/oxford)))
      (let [replay-id (first children)
            active-is-spliced? (= active-knot-id knot-id)]
        (cond-> (-> session
                    capture-undo
                    (update :tree tree/splice-out knot-id)
                    (assoc :new-id (or replay-id parent-id)))
          ;; Replay to first child if exists (process state still valid for that path)
          replay-id (do-replay-to! replay-id)
          ;; If we spliced the active knot and no children, clear active-knot-id
          (and active-is-spliced? (not replay-id)) (dissoc :active-knot-id))))))

(defn kill!
  "Kills the session, and the underlying process. Returns nil."
  [session]
  (sk.process/kill! (:process session)))

(defn deselect
  [session id]
  (-> session
      capture-undo
      (update :tree tree/deselect id)))
