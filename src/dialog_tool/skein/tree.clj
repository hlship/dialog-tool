(ns dialog-tool.skein.tree
  "Tree of Skein nodes.  Each knot represents one command in a chain of commands
  starting at the root knot. Each knot has a unique id.

  Nodes have a response and optionally an unblessed response."
  (:require [clojure.string :as string]))

(defn new-tree
  [seed]
  {:meta  {:seed seed}
   :knots {0 {:id    0
              :label "START"}}})

(def *next-id (atom (System/currentTimeMillis)))

(defn next-id
  []
  (swap! *next-id inc))

(defn- conj*
  [set k]
  (conj (or set #{}) k))

(defn add-child
  "Adds a child knot.  The response is initially unblessed."
  [tree parent-id new-id command response]
  (let [knot {:id        new-id
              :parent-id parent-id
              :command   command
              :unblessed response}]
    (-> tree
        (assoc-in [:knots new-id] knot)
        (update-in [:knots parent-id :children] conj* new-id))))

(defn rebuild-children
  "Rebuild the :children of each knot from the :parent-id of all other knots."
  [tree]
  (let [knots (-> tree :knots vals)
        parent->children (->> knots
                              (reduce (fn [m {:keys [id parent-id]}]
                                        (update m parent-id conj* id))
                                      {}))
        knots' (reduce (fn [m {:keys [id] :as knot}]
                         (assoc m id (assoc knot :children (parent->children id))))
                       {}
                       knots)]
    (assoc tree :knots knots')))

(defn delete-knot
  "Deletes a previously added knot, and any children below it."
  [tree knot-id]
  (let [knot (get-in tree [:knots knot-id])
        {:keys [children]} knot]
    (-> (reduce delete-knot tree children)
        (update :knots dissoc knot-id)
        ;; Yes, we don't care about efficiency!
        rebuild-children)))

(defn label-knot
  [tree knot-id label]
  (if (string/blank? label)
    (update-in tree [:knots knot-id] dissoc :label)
    (assoc-in tree [:knots knot-id :label] label)))

(defn- bless-knot
  [knot]
  (if (contains? knot :unblessed)
    (-> knot
        (dissoc :unblessed)
        (assoc :response (:unblessed knot)))
    knot))

(defn bless-response
  "Blesses a knot's response by rolling the unblessed response into the main response.
  Does nothing if the knot has no unblessed response."
  [tree knot-id]
  (update-in tree [:knots knot-id] bless-knot))

(defn- store-response
  [knot response]
  (if (= (:response knot) response)
    (dissoc knot :unblessed)
    (assoc knot :unblessed response)))

(defn update-response
  "Adds a response to a knot; if the new response matches existing :response,
  the knot is unchanged, otherwise updates the knot adding :unblessed
  with the new response."
  [tree knot-id new-response]
  (update-in tree [:knots knot-id] store-response new-response))

(defn find-children
  [tree knot-id]
  (let [{:keys [knots]} tree]
    (->> (get-in knots [knot-id :children])
         (map knots))))

(defn find-child-id
  "Looks in the children of the given knot for an existing knot with
  the indicated command string; if found, returns the child knot's id, otherwise
   returns nil."
  [tree knot-id command]
  (->> (find-children tree knot-id)
       (filter #(= command (:command %)))
       first
       :id))

(defn knot->wire
  "Converts a single knot for transfer over the wire to the browser."
  [knot]
  (let [{:keys [parent-id]} knot]
    (-> knot
        (dissoc :parent-id)
        (assoc :parent_id parent-id)
        (update :children #(-> % sort vec)))))

(defn ->wire
  "Convert the knots of a tree to a format suitable for transfer over the wire
  to the UI."
  [tree]
  (->> tree
       :knots
       vals
       (map #(knot->wire %))
       (sort-by :id)))

(defn all-knots
  "Returns all knots in the tree, in an unspecified order."
  [tree]
  (->> tree :knots vals))

(defn leaf-knots
  "Returns just the leaf knots (nodes without children), in an unspecified order."
  [tree]
  (->> tree
       all-knots
       (remove #(-> % :children seq))))

(defn change-command
  "Edits the command for a particular knot."
  [tree knot-id new-command]
  (assoc-in tree [:knots knot-id :command] new-command))

(defn insert-parent
  [tree knot-id new-id new-command]
  (let [parent-id (get-in tree [:knots knot-id :parent-id])]
    (-> tree
        (add-child parent-id new-id new-command nil)
        (assoc-in [:knots knot-id :parent-id] new-id)
        rebuild-children)))

(defn get-knot
  [tree knot-id]
  (get-in tree [:knots knot-id]))

(defn splice-out
  [tree knot-id]
  (let [{:keys [parent-id children]} (get-knot tree knot-id)]
    (-> (reduce (fn [tree child-id]
                  (assoc-in tree [:knots child-id :parent-id] parent-id))
                tree
                children)
        (update :knots dissoc knot-id)
        rebuild-children)))

(defn knots-from-root
  "Returns a seq of knots at or above the given knot in the tree; order is from
  knot 0 (the root) down to the initial knot."
  [tree initial-knot-id]
  (let [{:keys [knots]} tree]
    (loop [knot-id initial-knot-id
           result ()]
      (if (nil? knot-id)
        result
        (let [knot (get knots knot-id)]
          (recur (:parent-id knot)
                 (cons knot result)))))))