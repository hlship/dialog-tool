(ns dialog-tool.skein.tree-test
  (:require [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test :refer [match?]]
            [matcher-combinators.matchers :as m]
            [dialog-tool.skein.tree :as tree]))

(defn- make-tree
  "Helper to create a fresh tree for testing."
  []
  (tree/new-tree :dgdebug 12345))

(deftest tree-creation
  (testing "creates a tree with metadata and root knot"
    (let [tree (tree/new-tree :anything 12345)]
      (is (= {:meta              {:engine :anything
                                  :seed   12345}
              :knots             {0 {:id    0
                                     :label "START"}}
              :children          {}
              :selected          {}
              :status            {0 :new}
              :descendant-status {}}
             tree)))))

(deftest add-child-test
  (testing "adds a child knot with unblessed response"
    (let [tree  (make-tree)
          tree' (tree/add-child tree 0 1 "look" "You see a room.")]
      (is (match? {:knots    {0 {}
                              1 {:id        1
                                 :parent-id 0
                                 :command   "look"
                                 :unblessed "You see a room."}}
                   :children {0 #{1}}
                   :selected {0 1}}
                  tree'))))

  (testing "adds multiple children to same parent"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 0 2 "inventory" "Empty."))]

      (is (match?
            {:knots    {2 {:command   "inventory"
                           :unblessed "Empty."}}
             :children {0 #{1 2}}
             :selected {0 2}}
            tree)))))

(deftest delete-knot-test
  (testing "deletes a leaf knot"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/delete-knot 1))]
      (is (nil? (tree/get-knot tree 1)))

      (is (match? {:children          {0 #{}
                                       1 m/absent}
                   :knots             {1 m/absent}
                   :selected          (m/equals {})
                   :status            (m/equals {0 :new})
                   :descendant-status (m/equals {0 :ok})}
                  tree))))

  (testing "deletes a knot and all its descendants"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 1 2 "north" "Hallway.")
                   (tree/add-child 2 3 "east" "Kitchen.")
                   (tree/delete-knot 1))]
      (is (match? {:knots             {1 m/absent
                                       2 m/absent
                                       3 m/absent}
                   :status            (m/equals {0 :new})
                   :descendant-status (m/equals {0 :ok})
                   :children          (m/equals {0 #{}})
                   :selected          (m/equals {})}
                  tree))))

  (testing "adjusts selection when deleting selected child"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 0 2 "inventory" "Empty.")
                   (tree/delete-knot 2))]
      (is (match? {:knots    {2 m/absent}
                   :children (m/equals {0 #{1}})
                   :selected {0 1}}
                  tree)))))

(deftest label-knot-test
  (testing "adds a label to a knot"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/label-knot 1 "INTRO"))]
      (is (= "INTRO" (get-in tree [:knots 1 :label])))))

  (testing "removes label when blank"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/label-knot 1 "INTRO")
                   (tree/label-knot 1 ""))]
      (is (nil? (get-in tree [:knots 1 :label])))))

  (testing "removes label when nil"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/label-knot 1 "INTRO")
                   (tree/label-knot 1 nil))]
      (is (nil? (get-in tree [:knots 1 :label]))))))

(deftest bless-response-test
  (testing "blesses unblessed response"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "You see a room.")
                   (tree/bless-response 1))]
      (is (match? {:knots {1 {:response  "You see a room."
                              :unblessed m/absent}}}
                  tree))))

  (testing "does nothing if already blessed"
    (let [tree  (-> (make-tree)
                    (tree/add-child 0 1 "look" "You see a room.")
                    (tree/bless-response 1))
          tree' (tree/bless-response tree 1)]
      (is (= tree tree')))))

(deftest update-response-test
  (testing "adds unblessed when response differs from blessed"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/bless-response 1)
                   (tree/update-response 1 "Different room."))]
      (is (match? {:knots             {1 {:response  "Room."
                                          :unblessed "Different room."}}
                   :status            {0 :new
                                       1 :error}
                   :descendant-status {0 :error}}
                  tree))))

  (testing "removes unblessed when response matches blessed"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/bless-response 1)
                   (tree/update-response 1 "Different room.")
                   (tree/update-response 1 "Room."))]
      (is (match? {:knots {1 {:response  "Room."
                              :unblessed m/absent}}}
                  tree)))))

(deftest find-child-id-test
  (testing "finds child by command"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 0 2 "inventory" "Empty."))]
      (is (= 1 (tree/find-child-id tree 0 "look")))
      (is (= 2 (tree/find-child-id tree 0 "inventory")))))

  (testing "returns nil when command not found"
    (let [tree (make-tree)]
      (is (nil? (tree/find-child-id tree 0 "nonexistent"))))))

(deftest change-command-test
  (testing "changes the command of a knot"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/change-command 1 "examine room"))]
      (is (match? {:knots {1 {:command "examine room"}}}
                  tree)))))

(deftest insert-parent-test
  (testing "inserts a parent between root and child"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/insert-parent 1 2 "inventory"))]
      (is (match? {:knots    {2 {:command   "inventory"
                                 :response  m/absent
                                 :unblessed nil}}
                   :children {0 #{2}
                              2 #{1}}
                   :selected {0 2
                              1 m/absent
                              2 1}}
                  tree)))))

(deftest splice-out-test
  (testing "splices out a knot connecting children to parent"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 1 2 "north" "Hallway.")
                   (tree/splice-out 1))]
      (is (match? {:knots    {1 m/absent
                              2 {:parent-id 0}}
                   :selected {1 m/absent
                              0 2
                              2 m/absent}
                   :children {0 #{2}
                              1 m/absent
                              2 m/absent}}
                  tree))))

  (testing "splices out with multiple children"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 1 2 "north" "Hallway.")
                   (tree/add-child 1 3 "south" "Kitchen.")
                   (tree/splice-out 1))]
      (is (match? {:knots    {1 m/absent
                              2 {:parent-id 0}
                              3 {:parent-id 0}}
                   :selected {0 3                           ; why 3?  is this deterministic?
                              1 m/absent}
                   :children {0 #{2 3}
                              1 m/absent}}
                  tree)))))

(deftest status-propagation
  (let [tree (-> (make-tree)
                 (tree/update-response 0 "Initial game text.")
                 (tree/bless-response 0)
                 (tree/add-child 0 100 "look" "Room."))]
    (testing "new status propagates up"
      (is (match? {:status            {0   :ok
                                       100 :new}
                   :descendant-status {0 :new}}
                  tree)))

    (testing "error status overrides new"
      (is (match? {:status            {0   :ok
                                       100 :new
                                       200 :error}
                   :descendant-status {0   :error
                                       100 :error}}
                  (-> tree
                      (tree/add-child 100 200 "i" "inv")
                      (tree/bless-response 200)
                      (tree/update-response 200 "inventory")))))))

(deftest knots-from-root-test
  (testing "returns path from root to knot"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 1 2 "north" "Hallway."))
          path (tree/knots-from-root tree 2)]
      (is (= [0 1 2] (mapv :id path)))))

  (testing "returns just root for root knot"
    (let [tree (make-tree)
          path (tree/knots-from-root tree 0)]
      (is (= [0] (mapv :id path))))))

(deftest selected-knots-test
  (testing "returns selected path from root"
    (let [tree     (-> (make-tree)
                       (tree/add-child 0 1 "look" "Room.")
                       (tree/add-child 1 2 "north" "Hallway."))
          selected (tree/selected-knots tree)]
      (is (= [0 1 2] (mapv :id selected)))))

  (testing "stops at unselected knots"
    (let [tree     (-> (make-tree)
                       (tree/add-child 0 1 "look" "Room.")
                       (tree/add-child 1 2 "north" "Hallway.")
                       (tree/deselect 1))
          selected (tree/selected-knots tree)]
      (is (match? {:selected {0 1
                              1 m/absent}}
                  tree))
      (is (= [0 1] (map :id selected))))))

(deftest select-knot-test
  (testing "selects a knot and all ancestors"
    (let [tree  (-> (make-tree)
                    (tree/add-child 0 1 "look" "Room.")
                    (tree/add-child 0 2 "inventory" "Empty.")
                    (tree/add-child 2 3 "north" "Hallway."))
          tree' (tree/select-knot tree 3)]
      (is (= [0 2 3] (mapv :id (tree/selected-knots tree))))))

  (testing "does nothing when already selected"
    (let [tree  (-> (make-tree)
                    (tree/add-child 0 1 "look" "Room."))
          tree' (tree/select-knot tree 1)]
      (is (= tree tree')))))

(deftest find-by-label-test
  (testing "finds knot by label"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/label-knot 1 "INTRO"))]
      (is (match? {:id 1}
                  (tree/find-by-label tree "INTRO")))))

  (testing "returns nil when label not found"
    (let [tree (make-tree)]
      (is (nil? (tree/find-by-label tree "NONEXISTENT"))))))

(deftest labeled-knots-sorted-test
  (testing "returns START first, then others sorted"
    (let [tree    (-> (make-tree)
                      (tree/add-child 0 1 "look" "Room.")
                      (tree/label-knot 1 "ZEBRA")
                      (tree/add-child 0 2 "inventory" "Empty.")
                      (tree/label-knot 2 "ALPHA"))
          labeled (tree/labeled-knots-sorted tree)]
      (is (= ["START" "ALPHA" "ZEBRA"] (mapv :label labeled)))))

  (testing "excludes unlabeled knots"
    (let [tree    (-> (make-tree)
                      (tree/add-child 0 1 "look" "Room.")
                      (tree/add-child 0 2 "inventory" "Empty.")
                      (tree/label-knot 2 "LABELED"))
          labeled (tree/labeled-knots-sorted tree)]
      (is (= ["START" "LABELED"] (mapv :label labeled))))))


(deftest counts-test
  (testing "counts knot statuses"
    (let [tree   (-> (make-tree)
                     (tree/add-child 0 1 "look" "Room.")
                     (tree/bless-response 1)
                     (tree/add-child 1 2 "north" "Hallway.")
                     (tree/add-child 2 3 "east" "Kitchen.")
                     (tree/bless-response 3)
                     (tree/update-response 3 "Different."))
          counts (tree/totals tree)]
      (is (match? {:ok    1
                   :new   2
                   :error 1}
                  counts)))))                               ;; knot 3


(deftest leaf-knots-test
  (testing "returns only knots without children"
    (let [tree   (-> (make-tree)
                     (tree/add-child 0 1 "look" "Room.")
                     (tree/add-child 1 2 "north" "Hallway.")
                     (tree/add-child 0 3 "inventory" "Empty."))
          leaves (tree/leaf-knots tree)]
      (is (= #{2 3} (set (map :id leaves)))))))

(deftest all-knots-test
  (testing "returns all knots in tree"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 1 2 "north" "Hallway."))
          all  (tree/all-knots tree)]
      (is (= (-> tree :knots keys) (map :id all))))))
