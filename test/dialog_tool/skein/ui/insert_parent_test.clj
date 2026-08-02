(ns dialog-tool.skein.ui.insert-parent-test
  (:require [clojure.string :as string]
            [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test :refer [match?]]
            [dialog-tool.skein.ui.common :as common]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.helpers :refer [make-tree response]]))

;; ---------------------------------------------------------------------------
;; Test helpers

(defn- make-session
  []
  {:tree          (-> (make-tree)
                       (tree/add-child 0 1 "look" (response "You see a room."))
                       (tree/add-child 1 2 "north" (response "Hallway.")))
   :undo-stack    []
   :redo-stack    []
   :process-knot-id 0})

;; ---------------------------------------------------------------------------
;; Blank command guard tests

(deftest normalize-input-produces-blank-for-whitespace
  (testing "whitespace-only input normalizes to blank string"
    (is (string/blank? (common/normalize-input "   ")))))

(deftest normalize-input-produces-blank-for-newlines
  (testing "newline-only input normalizes to blank string"
    (is (string/blank? (common/normalize-input "\n\n")))))

(deftest normalize-input-preserves-meaningful-text
  (testing "meaningful text normalizes correctly"
    (is (= "look around" (common/normalize-input "  look   around  ")))))

(deftest blank-predicates-pass-the-guard
  (testing "all blank variants should pass string/blank? after normalize"
    (let [inputs [" " "" "\t\n" "    "]
          blanks? #(string/blank? (common/normalize-input %))]
      (is (every? blanks? inputs)))))

(deftest trim-collapses-interior-whitespace
  (testing "normalize collapses multiple spaces but preserves text"
    (is (= "a b c d"
           (common/normalize-input "  a   b     c  d  ")))))

;; ---------------------------------------------------------------------------
;; insert-parent! with blank command tests

(deftest insert-parent-with-blank-command-returns-unchanged-session
  (testing "blank command returns [nil session] unchanged - silent failure"
    (let [session (make-session)
          [error new-session] (session/insert-parent! session 1 "")]
      (is (nil? error))
      (is (= (:tree session) (:tree new-session))))))

(deftest insert-parent-with-whitespace-command-returns-unchanged-session
  (testing "whitespace command also returns unchanged session"
    (let [session (make-session)
          [error _] (session/insert-parent! session 1 "   ")]
      (is (nil? error)))))

;; ---------------------------------------------------------------------------
;; insert-parent! with valid command tests

(deftest insert-parent-with-valid-command-succeeds
  (testing "valid non-blank command produces a new knot"
    (let [session  (make-session)
          knots-before (count (:knots (:tree session)))
          [error new-session] (session/insert-parent! session 1 "wait")]
      (is (nil? error))
      (let [knots-after (count (:knots (:tree new-session)))]
        (is (> knots-after knots-before))))))

(deftest insert-parent-with-duplicate-on-parent-returns-error
  (testing "duplicate command on parent returns error"
    ;; Root (knot 0) already has a child with command "look" (knot 1).
    ;; Inserting "look" as parent of knot 1 should fail because root
    ;; already has that child command.
    (let [session (make-session)
          [error new-session] (session/insert-parent! session 1 "look")]
      (is (some? error))
      (is (= (:tree session) (:tree new-session))))))
