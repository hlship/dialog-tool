(ns dialog-tool.skein.tree
  "Tree of nodes."
  )

(defn new-tree
  [seed]
  {:meta     {:seed seed}
   :nodes    {0 {:id    0
                 :label "START"}}
   ;; id -> #{id ...} of child nodes
   :children {}})

(def *next-id (atom (System/currentTimeMillis)))

(defn next-id
  []
  (swap! *next-id inc))

(defn- conj*
  [set k]
  (conj (or set #{}) k))

(defn add-child
  "Adds a child node.  The text is added as :"
  [tree parent-id new-id command response]
  (let [node {:id        new-id
              :parent-id parent-id
              :command   command
              :unblessed response}]
    (-> tree
        (assoc-in [:nodes new-id] node)
        (update-in [:children parent-id] conj* new-id))))

(defn rebuild-children
  "Rebuilds the tree's :children index (this is used after reading
   a skein file)."
  [tree]
  (let [children (->> tree
                      :nodes
                      vals
                      (reduce (fn [m {:keys [id parent-id]}]
                                (if parent-id
                                  (update m parent-id conj* id)
                                  m))
                              {}))]
    (assoc tree :children children)))

(defn delete-node
  "Deletes a previously added node, and any children below it."
  [tree node-id]
  (let [children  (get-in tree [:children node-id])
        parent-id (get-in tree [:nodes node-id :parent-id])
        tree'     (reduce delete-node tree children)]
    (-> tree'
        (update :nodes dissoc node-id)
        (update-in [:children parent-id] disj node-id))))

(defn- bless-node*
  [node]
  (-> node
      (dissoc :unblessed)
      (assoc :response (:unblessed node))))

(defn bless-node
  [tree node-id]
  (update-in tree [:nodes node-id] bless-node*))

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
  (let [{:keys [children nodes]} tree]
    (->> (get children node-id)
         (map nodes)
         (filter #(= command (:command %)))
         first
         :id)))

