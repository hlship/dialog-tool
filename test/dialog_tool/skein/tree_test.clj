(ns dialog-tool.skein.tree-test
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test :refer [match?]]
            [matcher-combinators.matchers :as m]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.file :as file]
            [dialog-tool.helpers :refer [make-tree response]])
  (:import (clojure.lang LineNumberingPushbackReader)
           (java.io PrintWriter StringWriter StringReader)))


(deftest tree-creation
  (testing "creates a tree with metadata and root knot"
    (let [tree (tree/new-tree :anything 12345)]
      (is (= {:meta              {:engine :anything :seed 12345}
              :knots             {0 {:id     0
                                     :label  "START"
                                     :prompt :line}}
              :children          {}
              :selected          {}
              :active-knot-id    0
              :status            {0 :new}
              :descendant-status {}
              :expanded-ids      #{}}
             tree)))))

(deftest add-child-test
  (testing "adds a child knot with unblessed response"
    (let [tree  (make-tree)
          tree' (-> tree
                    (tree/add-child 0 1 "look" (response "You see a room."))
                    (tree/add-child 0 2 "i" {:response "I'm asking you for input"
                                             :prompt   :keystroke}))]
      (is (match? {:knots    {0 {}
                              1 {:id        1
                                 :parent-id 0
                                 :command   "look"
                                 :prompt    :line
                                 :unblessed "You see a room."}
                              2
                              {:id        2
                               :parent-id 0
                               :command   "i"
                               :prompt    :keystroke
                               :unblessed "I'm asking you for input"}}
                   :children {0 #{1 2}}
                   :selected {0 2}}
                  tree'))))

  (testing "adds multiple children to same parent"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/add-child 0 2 "inventory" (response "Empty.")))]

      (is (match?
            {:knots    {2 {:command   "inventory"
                           :unblessed "Empty."}}
             :children {0 #{1 2}}
             :selected {0 2}}
            tree)))))

(deftest delete-knot-test
  (testing "deletes a leaf knot"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
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
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/add-child 1 2 "north" (response "Hallway."))
                   (tree/add-child 2 3 "east" (response "Kitchen."))
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
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/add-child 0 2 "inventory" (response "Empty."))
                   (tree/delete-knot 2))]
      (is (match? {:knots    {2 m/absent}
                   :children (m/equals {0 #{1}})
                   :selected {0 1}}
                  tree)))))

(deftest label-knot-test
  (testing "adds a label to a knot"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/label-knot 1 "INTRO"))]
      (is (= "INTRO" (get-in tree [:knots 1 :label])))))

  (testing "removes label when blank"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/label-knot 1 "INTRO")
                   (tree/label-knot 1 ""))]
      (is (nil? (get-in tree [:knots 1 :label])))))

  (testing "removes label when nil"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/label-knot 1 "INTRO")
                   (tree/label-knot 1 nil))]
      (is (nil? (get-in tree [:knots 1 :label]))))))

(deftest bless-response-test
  (testing "blesses unblessed response"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "You see a room."))
                   (tree/bless-response 1))]
      (is (match? {:knots {1 {:response  "You see a room."
                              :unblessed m/absent}}}
                  tree))))

  (testing "does nothing if already blessed"
    (let [tree  (-> (make-tree)
                    (tree/add-child 0 1 "look" (response "You see a room."))
                    (tree/bless-response 1))
          tree' (tree/bless-response tree 1)]
      (is (= tree tree')))))

(deftest update-response-test
  (testing "adds unblessed when response differs from blessed"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/bless-response 1)
                   (tree/update-response 1 (response "Different room.")))]
      (is (match? {:knots             {1 (response "Room.")}
                   :status            {0 :new
                                       1 :error}
                   :descendant-status {0 :error}}
                  tree))))

  (testing "removes unblessed when response matches blessed"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/bless-response 1)
                   (tree/update-response 1 (response "Different room."))
                   (tree/update-response 1 (response "Room.")))]
      (is (match? {:knots {1 {:response  "Room."
                              :unblessed m/absent}}}
                  tree)))))

(deftest find-child-id-test
  (testing "finds child by command"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/add-child 0 2 "inventory" (response "Empty.")))]
      (is (= 1 (tree/find-child-id tree 0 "look")))
      (is (= 2 (tree/find-child-id tree 0 "inventory")))))

  (testing "returns nil when command not found"
    (let [tree (make-tree)]
      (is (nil? (tree/find-child-id tree 0 "nonexistent"))))))

(deftest change-command-test
  (testing "changes the command of a knot"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/change-command 1 "examine room"))]
      (is (match? {:knots {1 {:command "examine room"}}}
                  tree)))))

(deftest insert-parent-test
  (testing "inserts a parent between root and child"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
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
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/add-child 1 2 "north" (response "Hallway."))
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
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/add-child 1 2 "north" (response "Hallway."))
                   (tree/add-child 1 3 "south" (response "Kitchen."))
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
                 (tree/update-response 0 (response "Initial game text."))
                 (tree/bless-response 0)
                 (tree/add-child 0 100 "look" (response "Room.")))]
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
                      (tree/add-child 100 200 "i" (response "inv"))
                      (tree/bless-response 200)
                      (tree/update-response 200 (response "inventory"))))))))

(deftest knots-from-root-test
  (testing "returns path from root to knot"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/add-child 1 2 "north" (response "Hallway.")))
          path (tree/knots-from-root tree 2)]
      (is (= [0 1 2] (mapv :id path)))))

  (testing "returns just root for root knot"
    (let [tree (make-tree)
          path (tree/knots-from-root tree 0)]
      (is (= [0] (mapv :id path))))))

(deftest selected-knots-test
  (testing "returns selected path from root"
    (let [tree     (-> (make-tree)
                       (tree/add-child 0 1 "look" (response "Room."))
                       (tree/add-child 1 2 "north" (response "Hallway.")))
          selected (tree/selected-knots tree)]
      (is (= [0 1 2] (mapv :id selected)))))

  (testing "stops at unselected knots"
    (let [tree     (-> (make-tree)
                       (tree/add-child 0 1 "look" (response "Room."))
                       (tree/add-child 1 2 "north" (response "Hallway."))
                       (tree/deselect 1))
          selected (tree/selected-knots tree)]
      (is (match? {:selected {0 1
                              1 m/absent}}
                  tree))
      (is (= [0 1] (map :id selected))))))

(deftest select-knot-test
  (testing "selects a knot and all ancestors"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/add-child 0 2 "inventory" (response "Empty."))
                   (tree/add-child 2 3 "north" (response "Hallway.")))]
      (is (= [0 2 3] (mapv :id (tree/selected-knots tree))))))

  (testing "does nothing when already selected"
    (let [tree  (-> (make-tree)
                    (tree/add-child 0 1 "look" (response "Room.")))
          tree' (tree/select-knot tree 1)]
      (is (= tree tree')))))

(deftest find-by-label-test
  (testing "finds knot by label"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/label-knot 1 "INTRO"))]
      (is (match? {:id 1}
                  (tree/find-by-label tree "INTRO")))))

  (testing "returns nil when label not found"
    (let [tree (make-tree)]
      (is (nil? (tree/find-by-label tree "NONEXISTENT"))))))

(deftest labeled-knots-sorted-test
  (testing "returns START first, then others sorted"
    (let [tree    (-> (make-tree)
                      (tree/add-child 0 1 "look" (response "Room."))
                      (tree/label-knot 1 "ZEBRA")
                      (tree/add-child 0 2 "inventory" (response "Empty."))
                      (tree/label-knot 2 "ALPHA"))
          labeled (tree/labeled-knots-sorted tree)]
      (is (= ["START" "ALPHA" "ZEBRA"] (mapv :label labeled)))))

  (testing "excludes unlabeled knots"
    (let [tree    (-> (make-tree)
                      (tree/add-child 0 1 "look" (response "Room."))
                      (tree/add-child 0 2 "inventory" (response "Empty."))
                      (tree/label-knot 2 "LABELED"))
          labeled (tree/labeled-knots-sorted tree)]
      (is (= ["START" "LABELED"] (mapv :label labeled))))))

(deftest counts-test
  (testing "counts knot statuses"
    (let [tree   (-> (make-tree)
                     (tree/add-child 0 1 "look" (response "Room."))
                     (tree/bless-response 1)
                     (tree/add-child 1 2 "north" (response "Hallway."))
                     (tree/add-child 2 3 "east" (response "Kitchen."))
                     (tree/bless-response 3)
                     (tree/update-response 3 (response "Different.")))
          counts (tree/totals tree)]
      (is (match? {:ok    1
                   :new   2
                   :error 1}
                  counts)))))                               ;; knot 3

(deftest leaf-knots-test
  (testing "returns only knots without children"
    (let [tree   (-> (make-tree)
                     (tree/add-child 0 1 "look" (response "Room."))
                     (tree/add-child 1 2 "north" (response "Hallway."))
                     (tree/add-child 0 3 "inventory" (response "Empty.")))
          leaves (tree/leaf-knots tree)]
      (is (= #{2 3} (set (map :id leaves)))))))

(deftest all-knots-test
  (testing "returns all knots in tree"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/add-child 1 2 "north" (response "Hallway.")))
          all  (tree/all-knots tree)]
      (is (= (-> tree :knots keys) (map :id all))))))

(deftest set-locked-test
  (testing "locks a knot"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/set-locked 1 true))]
      (is (true? (get-in tree [:knots 1 :locked])))))

  (testing "unlocks a knot by removing :locked key"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/set-locked 1 true)
                   (tree/set-locked 1 false))]
      (is (match? {:knots {1 {:locked m/absent}}}
                  tree)))))

(deftest allow-deletion?-test
  (testing "returns false for a locked leaf"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/set-locked 1 true))]
      (is (false? (tree/allow-deletion? tree 1)))))

  (testing "returns false when a grandchild is locked"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/add-child 1 2 "north" (response "Hallway."))
                   (tree/set-locked 2 true))]
      (is (false? (tree/allow-deletion? tree 1)))))

  (testing "returns true for an unlocked subtree"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/add-child 1 2 "north" (response "Hallway.")))]
      (is (true? (tree/allow-deletion? tree 1)))))

  (testing "returns false when the root of the subtree is locked"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" (response "Room."))
                   (tree/add-child 1 2 "north" (response "Hallway."))
                   (tree/set-locked 1 true))]
      (is (false? (tree/allow-deletion? tree 1))))))

(defn- tree->string [tree]
  (let [sw (StringWriter.)
        pw (PrintWriter. sw)]
    (file/write-skein tree pw)
    (.flush pw)
    (.toString sw)))

(defn- string->tree [s]
  (-> s
      StringReader.
      LineNumberingPushbackReader.
      file/read-tree))

(deftest file-round-trip-locked-test
  (testing "locked flag persists through write/read cycle"
    (let [tree       (-> (make-tree)
                         (tree/add-child 0 1 "look" (response "Room."))
                         (tree/bless-response 1)
                         (tree/add-child 0 2 "inventory" (response "Empty."))
                         (tree/bless-response 2)
                         (tree/set-locked 1 true))
          serialized (tree->string tree)
          loaded     (string->tree serialized)]
      (is (true? (get-in loaded [:knots 1 :locked]))
          "Locked knot should have :locked true after round-trip")
      (is (nil? (get-in loaded [:knots 2 :locked]))
          "Unlocked knot should have no :locked key after round-trip")
      (is (string/includes? serialized "locked: true")
          "Serialized form should contain 'locked: true'"))))

(deftest update-dynamic
  (let [dynamic-response (-> "dynamic-small.txt" io/resource slurp)
        tree             (-> (make-tree)
                             (tree/add-child 0 1 "look" (response "Room."))
                             (tree/add-child 1 2 "north" (response "Hallway."))
                             (tree/update-dynamic 2 dynamic-response))]
    (is (match?
          {:dynamic-response dynamic-response
           :dynamic-state    (m/equals {:flags #{"(sand-dancer is named)"
                                                 "(#drawer is closed)"}
                                        :vars  {"(#flashlight is $ $)"     "(#flashlight is #heldby #knock)"
                                                "(remaining cigarettes $)" "(remaining cigarettes 6)"}})}
          (tree/get-knot tree 2)))))
