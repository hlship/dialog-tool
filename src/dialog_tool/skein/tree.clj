(ns dialog-tool.skein.tree
  "Tree of Skein nodes.  Each knot represents one command in a chain of commands
  starting at the root knot. Each knot has a unique numeric id.
  
  A knot is a map with keys :id, :parent-id, :command, :label (optional string),
  :response, and :unblessed.

  Knots usually have a response (unless newly created) and optionally an unblessed response."
  (:require [clojure.string :as string]
            [dialog-tool.skein.dynamic :as dynamic]))

(defn new-tree
  [engine seed]
  {:meta              {:engine engine
                       :seed   seed}
   :knots             {0 {:id    0
                          :label "START"}}
   ;; knot-id -> #{knot-id}
   :children          {}
   ;; knot-id -> knot-id (selected child)
   :selected          {}
   ;; knot-id -> status (just for knot)
   :status            {0 :new}
   ;; knot-id -> status (derived from children of knot)
   :descendant-status {}})

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

(defn- get-parent-id
  [tree knot-id]
  (get-in tree [:knots knot-id :parent-id]))

(defn greatest-status
  "Compares two status values and returns the greatest (:error, then :new, then :ok)."
  [s1 s2]
  (cond
    (= :error s1)
    :error

    (= :error s2)
    :error

    (= :ok s1)
    (or s2 :ok)

    (nil? s1)
    (or s2 :ok)

    :else                                                   ; (= :new s1)
    (if (= :error s2) :error :new)))

(defn- compute-descendant-status
  [tree knot-id]
  (let [{:keys [children status descendant-status]} tree
        child-ids (get children knot-id)]
    (reduce (fn [result child-id]
              (if (= result :error)
                (reduced :error)
                (let [child-status (greatest-status (descendant-status child-id) (status child-id))]
                  (greatest-status child-status result))))
            :ok
            child-ids)))

(defn- propagate-status
  "Updates the :descendant-status of the indicated knot and its parents, to the root.
  
  The descendant-status of a knot is the greatest of any of its childrens' status
  or descendant-status."
  [tree knot-id]
  (let [existing (get-in tree [:descendant-status knot-id])
        computed (compute-descendant-status tree knot-id)
        tree'    (if (= existing computed)
                   tree
                   (assoc-in tree [:descendant-status knot-id] computed))
        parent-id (get-parent-id tree' knot-id)]
    (if parent-id
      (recur tree' parent-id)
      tree')))

(defn- compute-knot-status
  "Returns the status of a knot: :ok if blessed matches response (no unblessed),
  :new if there's no response yet (only unblessed), or :error if they differ."
  [{:keys [unblessed response]}]
  (cond
    (nil? unblessed)
    :ok

    (nil? response)
    :new

    :else
    :error))

(defn add-child
  "Adds a child knot.  The response is initially unblessed, and the knot's status is :new."
  [tree parent-id new-id command response]
  (let [knot {:id        new-id
              :parent-id parent-id
              :command   command
              :unblessed response}]
    (-> tree
        (assoc-in [:knots new-id] knot)
        (assoc-in [:status new-id] :new)
        (add-child-id parent-id new-id)
        (set-selected parent-id new-id)
        (propagate-status parent-id))))

(defn rebuild
  "Rebuilds a tree as loaded from a file, populating the :children, :selected, :status, 
  and :descendant-status maps."
  [tree]
  (let [knots            (-> tree :knots vals)
        parent->children (->> knots
                              (reduce (fn [m {:keys [id parent-id]}]
                                        (if parent-id
                                          (update m parent-id conj* id)
                                          m))
                                      {}))
        selected         (reduce-kv
                           (fn [selected parent-id child-ids]
                             (assoc selected parent-id (first child-ids)))
                           {}
                           parent->children)
        status           (reduce (fn [status {:keys [id] :as knot}]
                                   (assoc status id (compute-knot-status knot)))
                                 {}
                                 knots)
        leaf-ids         (->> knots
                              (map :id)
                              (remove #(-> % parent->children seq)))
        tree'            (assoc tree
                                :children parent->children
                                :status status
                                :selected selected)]
    (reduce propagate-status tree' leaf-ids)))

(defn- adjust-selection-after-deletion
  [tree parent-id child-id]
  (let [selected (get-selected tree parent-id)
        children (get-children tree parent-id)]
    (cond

      (not= child-id selected)
      tree

      (seq children)
      (set-selected tree parent-id (first children))

      :else
      (clear-selected tree parent-id))))

(defn- delete-knot*
  [tree knot-id]
  (let [children (get-children tree knot-id)]
    (-> (reduce delete-knot* tree children)
        (update :children dissoc knot-id)
        (update :selected dissoc knot-id)
        (update :knots dissoc knot-id)
        (update :status dissoc knot-id)
        (update :descendant-status dissoc knot-id))))

(defn delete-knot
  "Deletes a previously added knot, and any children below it."
  [tree knot-id]
  (let [parent-id (get-parent-id tree knot-id)]
    (-> (delete-knot* tree knot-id)
        (update-in [:children parent-id] disj knot-id)
        (adjust-selection-after-deletion parent-id knot-id)
        (propagate-status parent-id))))

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

(defn- update-status
  [tree knot-id]
  (let [knot   (get-in tree [:knots knot-id])
        status (compute-knot-status knot)]
    (-> tree
        (assoc-in [:status knot-id] status)
        (propagate-status knot-id))))

(defn bless-response
  "Blesses a knot's response by rolling the unblessed response into the main response.
  Does nothing if the knot has no unblessed response."
  [tree knot-id]
  (-> tree
      (update-in [:knots knot-id] bless-knot)
      (update-status knot-id)))

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
  (let [tree'  (update-in tree [:knots knot-id] store-response new-response)
        status (compute-knot-status (get-in tree' [:knots knot-id]))]
    (-> tree'
        (assoc-in [:status knot-id] status)
        (propagate-status knot-id))))

(defn update-dynamic
  "Records the state of the dynamic predicates for the knot; this is the result
  of executing the @dynamic command (in the debugger) immediately after the primary command."
  [tree knot-id dynamic]
  (let [predicate-state (dynamic/parse dynamic)]
    (update-in tree [:dynamic knot-id]
               assoc :response dynamic
               :state predicate-state)))

(defn find-children
  "Finds all the immediate children knots of the provided knot (in an unspecified order)."
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
  "Edits the command for a particular knot.  This does not immediately affect the status of the knot,
  until the command is re-executed and a new response is applied."
  [tree knot-id new-command]
  (assoc-in tree [:knots knot-id :command] new-command))

(defn insert-parent
  "Inserts a new node with id new-parent-id and command new-command as the new parent
   of knot-id."
  [tree knot-id new-parent-id new-command]
  (let [old-parent-id (get-parent-id tree knot-id)]
    (-> tree
        (update-in [:children old-parent-id] disj knot-id)
        (update-in [:children new-parent-id] conj* knot-id)
        (add-child old-parent-id new-parent-id new-command nil)
        (assoc-in [:knots knot-id :parent-id] new-parent-id)
        (set-selected old-parent-id new-parent-id)
        (set-selected new-parent-id knot-id)
        (propagate-status knot-id))))

(defn splice-out
  "Delete a knot, reparenting any children of the knot to the current parent of the knot.
  Does not check for command collisions."
  [tree knot-id]
  (let [parent-id (get-parent-id tree knot-id)
        child-ids (get-children tree knot-id)
        tree'     (-> (reduce (fn [tree child-id]
                                (assoc-in tree [:knots child-id :parent-id] parent-id))
                              tree
                              child-ids)
                      (update :children dissoc knot-id)
                      (update-in [:children parent-id] into child-ids)
                      (update-in [:children parent-id] disj knot-id)
                      (delete-knot* knot-id)
                      (adjust-selection-after-deletion parent-id knot-id))]
    (reduce propagate-status tree' child-ids)))


(defn get-knot
  "Returns the knot with the given id."
  [tree knot-id]
  (when-let [knot (get-in tree [:knots knot-id])]
    (assoc knot
           :children (get-in tree [:children knot-id])
           :status (get-in tree [:status knot-id])
           :descendant-status (get-in tree [:descendant-status knot-id])
           :dynamic-response (get-in tree [:dynamic knot-id :response])
                             :dynamic-state (get-in tree [:dynamic knot-id :state]))))

(defn knots-from-root
  "Returns a seq of knots at or above the given knot in the tree; order is from
  knot 0 (the root) down to the initial knot."
  [tree initial-knot-id]
  (loop [knot-id initial-knot-id
         result  ()]
    (if (nil? knot-id)
      result
      (let [knot (get-knot tree knot-id)]
        (recur (:parent-id knot)
               (cons knot result))))))

(defn selected-knots
  "Starting at the root knot, returns a seq of each selected knot."
  [tree]
  (loop [result  []
         knot-id 0]
    (if-not knot-id
      result
      (let [knot (get-knot tree knot-id)]
        (recur (conj result knot)
               (get-selected tree knot-id))))))

(defn select-knot
  "Updates the parent of the indicated knot to make this knot selected, then recurses upwards
  doing the same until it hits the root."
  [tree knot-id]
  (if (= 0 knot-id)
    tree
    (let [parent-id (get-parent-id tree knot-id)]
      (recur (set-selected tree parent-id knot-id) parent-id))))

(defn deselect
  "Unselects any child as the selection for the indicated parent knot."
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
  (let [start-knot  (get-in tree [:knots 0])
        other-knots (->> (dissoc (:knots tree) 0)
                         vals
                         (remove #(string/blank? (:label %)))
                         (sort-by :label))]
    (cons start-knot other-knots)))

(defn knot-status
  "Returns the status of a knot: :ok if blessed matches response (no unblessed),
  :new if there's no response yet (only unblessed), or :error if they differ."
  [tree knot-id]
  (get-in tree [:status knot-id]))

(defn totals
  "Returns a map of :ok, :new, :error, each a count. :new is for new knots where there's
  not a :response, just :unblessed.  :error is when blessed != response."
  [tree]
  (->> tree
       :status
       vals
       frequencies
       (merge {:ok 0 :new 0 :error 0})))

(defn children
  "Returns the children of the knot, or nil if no children. Children are returned in no specific order."
  [tree knot]
  (when-let [ids (get-children tree (:id knot))]
    (mapv #(get-in tree [:knots %]) ids)))

(defn descendant-status
  "Returns the descendant status of the knot as :ok, :new, or :error.
  This is calculated as the most severe value of any of the children's
  statuses, or any of the children's descendant-statuses."
  [tree knot-id]
  (get-in tree [:descendant-status knot-id]))
