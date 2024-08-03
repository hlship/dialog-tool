(ns dialog-tool.skein.session
  "A session wraps around a Skein process, maintaining a tree, the process, and the current node."
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
     :process        process
     :tree           (tree/update-response tree 0 initial-response)
     :active-node-id 0}))

(defn create-new!
  "Creates a new session from an existing debug process.  The process should be
  just started, so that we can read the initial response."
  [process skein-path]
  (create-loaded! process skein-path (tree/new-tree (:seed process))))

(defn command!
  "Sends a player command to the process at the current node. This will either
  update a child of the current node (with a possibly unblessed response) or
  will create a new child node.

  Returns the updated session."
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

(defn- collect-commands
  [{:keys [nodes]} initial-node-id]
  (loop [node-id initial-node-id
         commands ()]
    (if (zero? node-id)
      commands
      (let [{:keys [command parent-id]} (get nodes node-id)]
        (recur parent-id
               (cons command commands))))))

(defn restart!
  "Restarts the game, sets the active node to 0."
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

(defn replay-to!
  "Restarts the game, then plays through all the commands leading up to the node.
  This will either verify that each node's response is unchanged, or capture
  unblessed responses to be verified."
  [session node-id]
  ;; TODO: optimize for case where already at the replay point, or active node is a parent
  ;; of replay point.
  (let [commands (collect-commands (:tree session) node-id)]
    (reduce command! (restart! session) commands)))

(defn bless
  [session node-id]
  (update session :tree tree/bless-response node-id))

(defn bless-all
  [session]
  (let [ids (-> session :tree :nodes keys)]
    (reduce bless session ids)))

(defn save!
  "Saves the current tree state to the file."
  [session]
  (let [{:keys [tree skein-path]} session]
    (sk.file/save-skein tree skein-path)
    session))

(defn kill!
  "Kills the session, and the underlying process. Returns nil."
  [session]
  (sk.process/kill! (:process session)))