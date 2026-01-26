(ns dialog-tool.skein.tree
  "Tree of Skein nodes.  Each knot represents one command in a chain of commands
  starting at the root knot. Each knot has a unique id.

  Knots usually have a response (unless newly created) and optionally an unblessed response."
  (:require [clojure.string :as string]))

(defn new-tree
  [engine seed]
  {:meta {:engine engine
          :seed seed}
   :knots {0 {:id 0
              :label "START"}}
   :children {}
   :selected {}})

(def *next-id (atom (System/currentTimeMillis)))

(defn next-id
  []
  (swap! *next-id inc))

(defn- conj*
  [set k]
  (conj (or set #{}) k))

;; Helper functions for accessing/updating :children and :selected maps

(defn- get-children
  "Gets the set of child IDs for a parent knot."
  [tree parent-id]
  (get-in tree [:children parent-id]))

(defn- add-child-id
  "Adds a child ID to the parent's children set."
  [tree parent-id child-id]
  (update-in tree [:children parent-id] conj* child-id))

(defn- remove-child-id
  "Removes a child ID from the parent's children set."
  [tree parent-id child-id]
  (update-in tree [:children parent-id] disj child-id))

(defn- get-selected
  "Gets the selected child ID for a parent knot."
  [tree parent-id]
  (get-in tree [:selected parent-id]))

(defn- set-selected
  "Sets the selected child ID for a parent knot."
  [tree parent-id child-id]
  (assoc-in tree [:selected parent-id] child-id))

(defn- clear-selected
  "Removes the selection for a parent knot."
  [tree parent-id]
  (update tree :selected dissoc parent-id))

(defn get-parent-id
  "Returns the parent knot id of the indicated knot, or nil if knot-id is the root knot (0)."
  [tree knot-id]
  (get-in tree [:knots knot-id :parent-id]))

(defn add-child
  "Adds a child knot.  The response is initially unblessed."
  [tree parent-id new-id command response]
  (let [knot {:id new-id
              :parent-id parent-id
              :command command
              :unblessed response}]
    (-> tree
        (assoc-in [:knots new-id] knot)
        (add-child-id parent-id new-id)
        (set-selected parent-id new-id))))

(defn rebuild-children
  "Rebuild the :children map from the :parent-id of all knots. Preserves existing :selected map."
  [tree]
  (let [knots (-> tree :knots vals)
        parent->children (->> knots
                              (reduce (fn [m {:keys [id parent-id]}]
                                        (update m parent-id conj* id))
                                      {}))]
    (assoc tree :children parent->children)))

(defn get-knot
  [tree knot-id]
  (get-in tree [:knots knot-id]))

(defn- adjust-selection-after-deletion
  [tree parent-id child-id]
  (let [selected (get-selected tree parent-id)
        children (get-children tree parent-id)]
    (cond

      (not= child-id selected)
      tree

      ;; Called after rebuild-children, so child-id will already have been
      ;; removed from the :children map, which may be nil or empty.
      (seq children)
      (set-selected tree parent-id (first children))

      :else
      (clear-selected tree parent-id))))

(defn delete-knot
  "Deletes a previously added knot, and any children below it."
  [tree knot-id]
  (let [knot (get-in tree [:knots knot-id])
        {:keys [parent-id]} knot
        children (get-children tree knot-id)]
    (-> (reduce delete-knot tree children)
        (update :knots dissoc knot-id)
        ;; Yes, we don't care about efficiency!
        rebuild-children
        (adjust-selection-after-deletion parent-id knot-id))))

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
  (let [{:keys [knots]} tree
        child-ids (get-children tree knot-id)]
    (map knots child-ids)))

(defn find-child-id
  "Looks in the children of the given knot for an existing knot with
  the indicated command string; if found, returns the child knot's id, otherwise
   returns nil."
  [tree knot-id command]
  (->> (find-children tree knot-id)
       (filter #(= command (:command %)))
       first
       :id))

(defn all-knots
  "Returns all knots in the tree, in an unspecified order."
  [tree]
  (->> tree :knots vals))

(defn leaf-knots
  "Returns just the leaf knots (nodes without children), in an unspecified order."
  [tree]
  (let [parent-ids (set (keys (:children tree)))]
    (->> tree
         all-knots
         (remove #(contains? parent-ids (:id %))))))

(defn change-command
  "Edits the command for a particular knot."
  [tree knot-id new-command]
  (assoc-in tree [:knots knot-id :command] new-command))

(defn insert-parent
  "Inserts a new node with id new-parent-id and command new-command as the new parent
   of knot-id."
  [tree knot-id new-parent-id new-command]
  (let [{:keys [parent-id]} (get-in tree [:knots knot-id])]
    (-> tree
        (add-child parent-id new-parent-id new-command nil)
        (assoc-in [:knots knot-id :parent-id] new-parent-id)
        rebuild-children
        (set-selected new-parent-id knot-id))))

(defn splice-out
  [tree knot-id]
  (let [{:keys [parent-id]} (get-knot tree knot-id)
        children (get-children tree knot-id)]
    (-> (reduce (fn [tree child-id]
                  (assoc-in tree [:knots child-id :parent-id] parent-id))
                tree
                children)
        (update :knots dissoc knot-id)
        rebuild-children
        (adjust-selection-after-deletion parent-id knot-id))))

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

(defn selected-knots
  "Starting at the root knot, returns a seq of each selected knot."
  [tree]
  (let [{:keys [knots]} tree]
    (loop [result []
           knot-id 0]
      (if-not knot-id
        result
        (let [knot (get knots knot-id)]
          (recur (conj result knot)
                 (get-selected tree knot-id)))))))

(defn apply-default-selections
  "Called after loading the tree from a file, it sets up default selections for each knot as the first child."
  [tree]
  (reduce (fn [tree [parent-id child-ids]]
            (if (seq child-ids)
              (set-selected tree parent-id (first child-ids))
              tree))
          tree
          (:children tree)))

(defn select-knot
  "Updates the parent of the indicated knot to make this knot selected, then recurses upwards
  doing the same until it hits the root.  Never marks the tree dirty."
  [tree knot-id]
  (loop [knot-id knot-id
         tree tree]
    (if (= 0 knot-id)
      tree
      (let [{:keys [parent-id]} (get-knot tree knot-id)
            selected (get-selected tree parent-id)]
        (if (= knot-id selected)
          tree
          (recur parent-id (set-selected tree parent-id knot-id)))))))

(defn deselect
  [tree knot-id]
  (clear-selected tree knot-id))

(defn find-by-label
  [tree label]
  (->> tree
       all-knots
       (filter #(= label (:label %)))
       first))

(defn labeled-knots-sorted
  "Returns a seq of knots that have non-blank labels, sorted alphabetically by label.
   The START knot (root knot with id 0) is always first."
  [tree]
  (let [start-knot (get-knot tree 0)
        other-knots (->> (dissoc (:knots tree) 0)
                         vals
                         (remove #(string/blank? (:label %)))
                         (sort-by :label))]
    (cons start-knot other-knots)))

(defn assess-knot
  "Returns the category of a knot: :ok if blessed matches response (no unblessed),
  :new if there's no response yet (only unblessed), or :error if they differ."
  [{:keys [unblessed response]}]
  (cond
    (nil? unblessed)
    :ok

    (nil? response)
    :new

    :else
    :error))

(defn counts
  "Returns a map of :ok, :new, :error, each a count. :new is for new knots where there's
  not a :response, just :unblessed.  :error is when blessed != response."
  [tree]
  (->> tree
       :knots
       vals
       (map assess-knot)
       frequencies
       (merge {:ok 0 :new 0 :error 0})))

(defn children
  "Returns the children of the knot, or nil if no children."
  [tree knot]
  (when-let [ids (get-children tree (:id knot))]
    (map #(get-in tree [:knots %]) ids)))

(defn compute-descendant-status
  "Returns a map of knot-id -> worst status among all descendants.
   Status is :error if any descendant has an error, :new if any is new, :ok otherwise.
   Uses post-order traversal to bubble up worst status from leaves."
  [tree]
  (let [knots (:knots tree)]
    (letfn [(compute-status [knot-id]
              (let [knot (knots knot-id)
                    own-status (assess-knot knot)
                    child-ids (get-children tree knot-id)
                    child-statuses (map compute-status child-ids)
                    worst-child-status (cond
                                         (some #{:error} child-statuses) :error
                                         (some #{:new} child-statuses) :new
                                         :else :ok)
                    worst-status (cond
                                   (= :error own-status) :error
                                   (= :error worst-child-status) :error
                                   (= :new own-status) :new
                                   (= :new worst-child-status) :new
                                   :else :ok)]
                worst-status))]
      (into {}
            (map (fn [knot-id]
                   [knot-id (compute-status knot-id)]))
            (keys knots)))))
