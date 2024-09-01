(ns dialog-tool.skein.session
  "A session wraps around a Skein process, maintaining a tree, the process, and the current node.
  In addition, there's an undo and redo stack, representing prior states of the tree."
  (:require [dialog-tool.skein.file :as sk.file]
            [dialog-tool.skein.process :as sk.process]
            [dialog-tool.skein.tree :as tree]))

(defn create-loaded!
  "Creates a new session from a tree loaded from the path, and a process started with
  the skein's seed. The process should be just started so that we can read the initial
  response."
  [process skein-path tree]
  (let [initial-response (sk.process/read-response! process)]
    {:skein-path     skein-path
     :undo-stack     []
     :redo-stack     []
     :process        process
     :tree           (tree/update-response tree 0 initial-response)
     :active-node-id 0}))

(defn create-new!
  "Creates a new session from an existing debug process.  The process should be
  just started, so that we can read the initial response."
  [process skein-path]
  (create-loaded! process skein-path (tree/new-tree (:seed process))))

(defn enable-undo
  [session undo-enabled?]
  (assoc session :undo-enabled? undo-enabled?))

(defn- capture-undo
  [session]
  (if-not (:undo-enabled? session)
    session
    (-> session
        (update :undo-stack conj (:tree session))
        ;; TODO: Really shouldn't clear it unless :tree changed at end; macro time?
        (update :redo-stack empty))))

(defn- run-command!
  [session command]
  (let [{:keys [tree active-node-id process]} session
        child-node-id (tree/find-child-id tree active-node-id command)
        response (sk.process/send-command! process command)]
    (if child-node-id
      (-> session
          (assoc :active-node-id child-node-id)
          (update :tree tree/update-response child-node-id response))
      (let [new-node-id (tree/next-id)]
        (-> session
            (assoc :active-node-id new-node-id)
            (update :tree tree/add-child active-node-id new-node-id command response))))))

(defn command!
  "Sends a player command to the process at the current node. This will either
  update a child of the current node (with a possibly unblessed response) or
  will create a new child node.

  Returns the updated session."
  [session command]
  (-> session
      capture-undo
      (run-command! command)))

(defn- nodes-to
  "Returns a seq of nodes at or above the given node in the tree; order is from
  node 0 down to the initial node."
  [{:keys [nodes]} initial-node-id]
  (loop [node-id initial-node-id
         result ()]
    (if (nil? node-id)
      result
      (let [node (get nodes node-id)]
        (recur (:parent-id node)
               (cons node result))))))

(defn- collect-commands
  [tree initial-node-id]
  (->> (nodes-to tree initial-node-id)
       (drop 1)                                             ; no command for root node
       (map :command)))

(defn- do-restart!
  [session]
  ;; First pass made use of '(restart)' but that had problems, and dgdebug is so fast
  ;; to start up that it doesn't make sense.
  (let [{:keys [process]} session
        process' (sk.process/restart! process)
        new-initial-response (sk.process/read-response! process')]
    (-> session
        (assoc :active-node-id 0
               :process process')
        (update :tree tree/update-response 0 new-initial-response))))

(defn restart!
  "Restarts the game, sets the active node to 0."
  [session]
  (-> session
      capture-undo
      do-restart!))

(defn replay-to!
  "Restarts the game, then plays through all the commands leading up to the node.
  This will either verify that each node's response is unchanged, or capture
  unblessed responses to be verified."
  [session node-id]
  (let [session' (capture-undo session)
        commands (collect-commands (:tree session') node-id)]
    (reduce run-command! (do-restart! session') commands)))

(defn- node-category
  [{:keys [unblessed response]}]
  (cond
    (nil? unblessed) :ok

    (some? response) :error

    :default :new))

(defn totals
  "Totals the number of nodes that are :ok, :new, or :error."
  [session]
  (->> session
       :tree
       tree/all-nodes
       (reduce (fn [counts node]
                 (update counts (node-category node) inc))
               {:ok 0 :new 0 :error 0})))

(defn bless
  [session node-id]
  (-> session
      capture-undo
      (update :tree tree/bless-response node-id)))

(defn bless-to
  [session node-id]
  (let [{:keys [tree]} session
        ids (->> (nodes-to tree node-id)
                 (map :id))]
    (-> session
        capture-undo
        (assoc :tree (reduce tree/bless-response tree ids)))))

(defn bless-all
  [session]
  (let [session' (capture-undo session)
        ids (-> session' :tree :nodes keys)]
    (assoc session :tree (reduce (fn [tree node-id]
                                   (tree/bless-response tree node-id))
                                 (:tree session')
                                 ids))))

(defn save!
  "Saves the current tree state to the file.  Does not affect undo/redo."
  [session]
  (let [{:keys [tree skein-path]} session]
    (sk.file/save-skein tree skein-path)
    session))

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
  [session]
  "Reverts an undo, restoring a prior state of the tree."
  (let [tree' (-> session :redo-stack peek)]
    (-> session
        (assoc :tree tree')
        (update :undo-stack conj (:tree session))
        (update :redo-stack pop))))

(defn label
  [session node-id label]
  (-> session
      capture-undo
      (update :tree tree/label-node node-id label)))

(defn delete
  "Deletes a node from the tree, including any descendants of the node."
  [session node-id]
  (-> session
      capture-undo
      (update :tree tree/delete-node node-id)))

(defn kill!
  "Kills the session, and the underlying process. Returns nil."
  [session]
  (sk.process/kill! (:process session)))
