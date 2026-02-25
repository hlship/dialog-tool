(ns dialog-tool.skein.session-test
  (:require [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test :refer [match?]]
            [matcher-combinators.matchers :as m]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.session :as session]))

(defn- make-tree
  []
  (tree/new-tree :dgdebug 12345))

(defn- make-session
  "Creates a minimal session map suitable for testing session operations."
  [tree]
  {:tree tree
   :undo-stack []
   :redo-stack []})

(deftest delete-locked-knot-test
  (testing "delete! returns error when target knot is locked"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/set-locked 1 true))
          session (make-session tree)
          [error session'] (session/delete! session 1)]
      (is (some? error))
      (is (= tree (:tree session')))))

  (testing "delete! returns error when a descendant is locked"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 1 2 "north" "Hallway.")
                   (tree/set-locked 2 true))
          session (make-session tree)
          [error session'] (session/delete! session 1)]
      (is (some? error))
      (is (= tree (:tree session')))))

  (testing "delete! succeeds when subtree has no locked knots"
    (let [tree (-> (make-tree)
                   (tree/add-child 0 1 "look" "Room.")
                   (tree/add-child 1 2 "north" "Hallway."))
          session (make-session tree)
          [error session'] (session/delete! session 1)]
      (is (nil? error))
      (is (match? {:knots {1 m/absent 2 m/absent}}
                  (:tree session'))))))
