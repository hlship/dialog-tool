(ns dialog-tool.skein.tree
  "Tree of Skein nodes.  Each node represents one command in a chain of commands
  starting at the root node. Each node has a unique id.

  Nodes have a response and optionally an unblessed response.")

(defn new-tree
  [seed]
  {:meta     {:seed seed}
   :nodes    {0 {:id    0
                 :label "START"}}})

(def *next-id (atom (System/currentTimeMillis)))

(defn next-id
  []
  (swap! *next-id inc))

(defn- conj*
  [set k]
  (conj (or set #{}) k))

(defn add-child
  "Adds a child node.  The response is initially unblessed."
  [tree parent-id new-id command response]
  (let [node {:id        new-id
              :parent-id parent-id
              :command   command
              :unblessed response}]
    (-> tree
        (assoc-in [:nodes new-id] node)
        (update-in [:nodes parent-id :children] conj* new-id))))

(defn rebuild-children
  "Rebuild the :children of each node from the :parent-id of all other nodes."
  [tree]
  (let [nodes (-> tree :nodes vals)
        parent->children (->> nodes
                              (reduce (fn [m {:keys [id parent-id]}]
                                        (update m parent-id conj* id))
                                      {}))
        nodes' (reduce (fn [m {:keys [id] :as node}]
                         (assoc m id (assoc node :children (parent->children id))))
                       {}
                       nodes)]
    (assoc tree :nodes nodes')))

(defn delete-node
  "Deletes a previously added node, and any children below it."
  [tree node-id]
  (let [node (get-in tree [:nodes node-id])
        {:keys [children]} node]
    (-> (reduce delete-node tree children)
        (update :nodes dissoc node-id)
        ;; Yes, we don't care about efficiency!
        rebuild-children)))

(defn- bless-node
  [node]
  (if (contains? node :unblessed)
    (-> node
        (dissoc :unblessed)
        (assoc :response (:unblessed node)))
    node))

(defn bless-response
  "Blesses a node's response by rolling the unblessed response into the main response.
  Does nothing if the node has no unblessed response."
  [tree node-id]
  (update-in tree [:nodes node-id] bless-node))

(defn- store-response
  [node response]
  (if (= (:response node) response)
    node
    (assoc node :unblessed response)))

(defn update-response
  "Adds a response to a node; if the new response matches existing :response,
  the node is unchanged, otherwise updates the node adding :unblessed
  with the new response."
  [tree node-id new-response]
  (update-in tree [:nodes node-id] store-response new-response))

(defn find-child-id
  "Looks in the children of the given node for an existing node with
  the indicated command string; if found, returns the node's id, otherwise
   returns nil."
  [tree node-id command]
  (let [{:keys [nodes]} tree]
    (->> (get-in nodes [node-id :children])
         (map nodes)
         (filter #(= command (:command %)))
         first
         :id)))

(defn node->wire
  [node]
  (let [{:keys [parent-id]} node]
    (-> node
        (dissoc :parent-id)
        (assoc :parent_id parent-id)
        (update :children #(-> % sort vec)))))

(defn ->wire
  "Convert the nodes of a tree to a format suitable for transfer over the wire`
  to the UI."
  [tree]
  (->> tree
       :nodes
       vals
       (map #(node->wire %))
       (sort-by :id)))

