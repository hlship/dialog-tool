(ns dialog-tool.skein.session-test
  (:require [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test :refer [match?]]
            [matcher-combinators.matchers :as m]
            [dialog-tool.skein.process :as sk.process]
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

(defn- make-process-session
  "Creates a session with a start-process-fn that spawns real processes, for
  testing functions that need to actually run the game engine."
  [tree engine seed]
  (let [start-process-fn (fn [& [opts]] (sk.process/start-process! nil engine seed opts))]
    (assoc (make-session tree)
           :start-process-fn start-process-fn
           :process nil)))

(deftest collect-replay-to-test
  (testing "apply-responses sets :unblessed when response differs"
    (let [tree     (-> (make-tree)
                       (tree/add-child 0 1 "look" "Old response."))
          session  (make-session tree)
          result   (session/apply-responses session {1 "New response."})
          new-knot (get-in result [:tree :knots 1])]
      (is (= "New response." (:unblessed new-knot)))))

  (testing "apply-responses clears :unblessed when response matches blessed :response"
    ;; add-child stores the response as :unblessed/:new; bless-response promotes
    ;; it to :response. Then update-response with a different value adds :unblessed.
    ;; Applying the original (blessed) response back should clear :unblessed.
    (let [tree     (-> (make-tree)
                       (tree/add-child 0 1 "look" "Same response.")
                       (tree/bless-response 1)
                       (tree/update-response 1 "Different."))
          session  (make-session tree)
          result   (session/apply-responses session {1 "Same response."})
          new-knot (get-in result [:tree :knots 1])]
      (is (nil? (:unblessed new-knot))))))

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
