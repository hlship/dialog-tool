(ns dialog-tool.skein.session
  "A session wraps around a Skein process, maintaining a tree, the process, and the current node."
  (:require [dialog-tool.skein.file :as sk.file]
            [dialog-tool.skein.process :as sk.process]
            [dialog-tool.project-file :as pf]
            [dialog-tool.skein.tree :as tree]))

(defn create-new!
  [debugger-path project seed skein-path]
  ;; TODO: May work better to just pass in the already started process
  (let [process (sk.process/start-debug-process! debugger-path
                                                 project
                                                 seed)
        initial-response (sk.process/read-response! process)
        tree (-> (tree/new-tree seed)
                 (tree/update-response 0 initial-response))]
    {:skein-path     skein-path
     :process        process
     :tree           tree
     :active-node-id 0}))

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
  (let [{:keys [process]} session
        new-initial-response (sk.process/send-command! process "(restart)")
        ;; TODO: Clip first line? "> (restart)"
        ;; TODO: restart maybe should be handled by sk.process
        ;; TODO: verify that RNG is reset too (otherwise, have to restart entire process).
        ]
    (-> session
        (assoc :active-node-id 0)
        (update :tree tree/update-response 0 new-initial-response))))

(defn replay-to!
  "Restarts the game, then plays through all the commands leading up to the node.
  This will either verify that each node's response is unchanged, or capture
  unblessed responses to be verified."
  [session node-id]
  (let [commands (collect-commands (:tree session) node-id)]
    (reduce command! (restart! session) commands)))

(defn bless
  [session node-id]
  (update session :tree tree/bless-response node-id))

(defn- bless-all
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

;; Temporary

^:clj-reload/keep
(def *session (atom nil))

(comment
  @*session
  (reset! *session (create-new! nil (pf/read-project "../sanddancer-dialog") 40000 "target/game.skein"))

  (swap! *session command! "open glove")
  (swap! *session command! "x pack")
  (swap! *session command! "smoke")
  (swap! *session command! "cigarette")
  (swap! *session bless 0)
  (swap! *session bless-all)
  (swap! *session restart!)
  (swap! *session command! "x truck")
  (swap! *session replay-to! 1722104090423)
  (swap! *session save!)
  (swap! *session kill!)
  )
