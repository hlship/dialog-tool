(ns dialog-tool.skein.tree-test
  (:require [clojure.test :refer [deftest is testing]]
            [dialog-tool.skein.tree :as tree]))

(defn- make-tree
  "Helper to create a fresh tree for testing."
  []
  (tree/new-tree :dgdebug 12345))

(deftest new-tree-test
  (testing "creates a tree with metadata and root knot"
    (let [tree (tree/new-tree :dgdebug 12345)]
      (is (= :dgdebug (get-in tree [:meta :engine])))
      (is (= 12345 (get-in tree [:meta :seed])))
      (is (= {:id 0 :label "START"} (tree/get-knot tree 0))))))

(deftest add-child-test
  (testing "adds a child knot with unblessed response"
    (let [tree (make-tree)
          tree' (tree/add-child tree 0 1 "look" "You see a room.")]
      (is (= 1 (get-in tree' [:knots 1 :id])))
      (is (= 0 (get-in tree' [:knots 1 :parent-id])))
      (is (= "look" (get-in tree' [:knots 1 :command])))
      (is (= "You see a room." (get-in tree' [:knots 1 :unblessed])))
      (is (nil? (get-in tree' [:knots 1 :response])))
      (is (= 1 (get-in tree' [:selected 0])))
      (is (= #{1} (get-in tree' [:children 0])))))

  (testing "adds multiple children to same parent"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 0 2 "inventory" "Empty."))]
      (is (= #{1 2} (get-in tree [:children 0])))
      (is (= 2 (get-in tree [:selected 0]))))))

(deftest delete-knot-test
  (testing "deletes a leaf knot"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room."))
          tree' (tree/delete-knot tree 1)]
      (is (nil? (tree/get-knot tree' 1)))
      (is (nil? (get-in tree' [:children 0])))
      (is (nil? (get-in tree' [:selected 0])))))

  (testing "deletes a knot and all its descendants"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 1 2 "north" "Hallway.")
                   (tree/add-child 2 3 "east" "Kitchen."))
          tree' (tree/delete-knot tree 1)]
      (is (nil? (tree/get-knot tree' 1)))
      (is (nil? (tree/get-knot tree' 2)))
      (is (nil? (tree/get-knot tree' 3)))
      (is (nil? (get-in tree' [:children 0])))))

  (testing "adjusts selection when deleting selected child"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 0 2 "inventory" "Empty."))
          tree' (tree/delete-knot tree 2)]
      (is (= 1 (get-in tree' [:selected 0]))))))

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
                   (tree/add-child 0 1 "look" "You see a room."))
          tree' (tree/bless-response tree 1)]
      (is (= "You see a room." (get-in tree' [:knots 1 :response])))
      (is (nil? (get-in tree' [:knots 1 :unblessed])))))

  (testing "does nothing if already blessed"
    (let [tree (-> (make-tree)
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
      (is (= "Room." (get-in tree [:knots 1 :response])))
      (is (= "Different room." (get-in tree [:knots 1 :unblessed])))))

  (testing "removes unblessed when response matches blessed"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/bless-response 1)
                   (tree/update-response 1 "Different room.")
                   (tree/update-response 1 "Room."))]
      (is (= "Room." (get-in tree [:knots 1 :response])))
      (is (nil? (get-in tree [:knots 1 :unblessed]))))))

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
      (is (= "examine room" (get-in tree [:knots 1 :command]))))))

(deftest insert-parent-test
  (testing "inserts a parent between root and child"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/insert-parent 1 2 "inventory"))
          knot-2 (tree/get-knot tree 2)]
      (is (= 0 (:parent-id knot-2)))
      (is (= "inventory" (:command knot-2)))
      (is (= #{1} (get-in tree [:children 2])))
      (is (= 2 (get-in tree [:knots 1 :parent-id])))))

  (testing "updates children relationships correctly"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/insert-parent 1 2 "inventory"))]
      (is (= #{2} (get-in tree [:children 0])))
      (is (= #{1} (get-in tree [:children 2]))))))

(deftest splice-out-test
  (testing "splices out a knot connecting children to parent"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 1 2 "north" "Hallway.")
                   (tree/splice-out 1))]
      (is (nil? (tree/get-knot tree 1)))
      (is (= 0 (get-in tree [:knots 2 :parent-id])))
      (is (= #{2} (get-in tree [:children 0])))))

  (testing "splices out with multiple children"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 1 2 "north" "Hallway.")
                   (tree/add-child 1 3 "south" "Kitchen.")
                   (tree/splice-out 1))]
      (is (nil? (tree/get-knot tree 1)))
      (is (= 0 (get-in tree [:knots 2 :parent-id])))
      (is (= 0 (get-in tree [:knots 3 :parent-id])))
      (is (= #{2 3} (get-in tree [:children 0]))))))

(deftest knots-from-root-test
  (testing "returns path from root to knot"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 1 2 "north" "Hallway."))
          path (tree/knots-from-root tree 2)]
      (is (= 3 (count path)))
      (is (= [0 1 2] (map :id path)))))

  (testing "returns just root for root knot"
    (let [tree (make-tree)
          path (tree/knots-from-root tree 0)]
      (is (= 1 (count path)))
      (is (= 0 (:id (first path)))))))

(deftest selected-knots-test
  (testing "returns selected path from root"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 1 2 "north" "Hallway."))
          selected (tree/selected-knots tree)]
      (is (= [0 1 2] (map :id selected)))))

  (testing "stops at unselected knots"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 1 2 "north" "Hallway.")
                   (tree/deselect 1))
          selected (tree/selected-knots tree)]
      (is (= [0 1] (map :id selected))))))

(deftest select-knot-test
  (testing "selects a knot and all ancestors"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 0 2 "inventory" "Empty.")
                   (tree/add-child 2 3 "north" "Hallway."))
          tree' (tree/select-knot tree 3)]
      (is (= 2 (get-in tree' [:selected 0])))
      (is (= 3 (get-in tree' [:selected 2])))))

  (testing "does nothing when already selected"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room."))
          tree' (tree/select-knot tree 1)]
      (is (= tree tree')))))

(deftest find-by-label-test
  (testing "finds knot by label"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/label-knot 1 "INTRO"))]
      (is (= 1 (:id (tree/find-by-label tree "INTRO"))))))

  (testing "returns nil when label not found"
    (let [tree (make-tree)]
      (is (nil? (tree/find-by-label tree "NONEXISTENT"))))))

(deftest labeled-knots-sorted-test
  (testing "returns START first, then others sorted"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/label-knot 1 "ZEBRA")
                   (tree/add-child 0 2 "inventory" "Empty.")
                   (tree/label-knot 2 "ALPHA"))
          labeled (tree/labeled-knots-sorted tree)]
      (is (= ["START" "ALPHA" "ZEBRA"] (map :label labeled)))))

  (testing "excludes unlabeled knots"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 0 2 "inventory" "Empty.")
                   (tree/label-knot 2 "LABELED"))
          labeled (tree/labeled-knots-sorted tree)]
      (is (= ["START" "LABELED"] (map :label labeled))))))

(deftest assess-knot-test
  (testing "returns :ok when no unblessed"
    (let [knot {:response "Room." :unblessed nil}]
      (is (= :ok (tree/assess-knot knot)))))

  (testing "returns :new when unblessed but no response"
    (let [knot {:response nil :unblessed "Room."}]
      (is (= :new (tree/assess-knot knot)))))

  (testing "returns :error when both response and unblessed present"
    (let [knot {:response "Room." :unblessed "Different."}]
      (is (= :error (tree/assess-knot knot))))))

(deftest counts-test
  (testing "counts knot statuses"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/bless-response 1)
                   (tree/add-child 1 2 "north" "Hallway.")
                   (tree/add-child 2 3 "east" "Kitchen.")
                   (tree/bless-response 3)
                   (tree/update-response 3 "Different."))
          counts (tree/counts tree)]
      (is (= 2 (:ok counts))) ;; root and knot 1
      (is (= 1 (:new counts))) ;; knot 2
      (is (= 1 (:error counts))))));; knot 3

(deftest compute-descendant-status-test
  (testing "computes worst status for each knot's descendants"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/bless-response 1)
                   (tree/add-child 1 2 "north" "Hallway.")
                   (tree/bless-response 2)
                   (tree/update-response 2 "Different."))
          status-map (tree/compute-descendant-status tree)]
      (is (= :error (status-map 0))) ;; root has error descendant
      (is (= :error (status-map 1))) ;; has error child
      (is (= :error (status-map 2))))) ;; is itself error

  (testing "bubbles up :new status when no errors"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/bless-response 1)
                   (tree/add-child 1 2 "north" "Hallway."))
          status-map (tree/compute-descendant-status tree)]
      (is (= :new (status-map 0))) ;; has new descendant
      (is (= :new (status-map 1))) ;; has new child
      (is (= :new (status-map 2))))) ;; is itself new

  (testing "returns :ok when all blessed"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/bless-response 1))
          status-map (tree/compute-descendant-status tree)]
      (is (= :ok (status-map 0))) ;; root is ok
      (is (= :ok (status-map 1))))))

(deftest apply-default-selections-test
  (testing "selects first child for each parent"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 0 2 "inventory" "Empty.")
                   (tree/deselect 0))
          tree' (tree/apply-default-selections tree)]
      (is (= 1 (get-in tree' [:selected 0]))))))

(deftest leaf-knots-test
  (testing "returns only knots without children"
    (let [tree (-> (make-tree)
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
          all (tree/all-knots tree)]
      (is (= #{0 1 2} (set (map :id all)))))))
