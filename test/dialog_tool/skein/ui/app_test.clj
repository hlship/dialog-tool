(ns dialog-tool.skein.ui.app-test
  (:require [clojure.string :as string]
            [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test :refer [match?]]
            [matcher-combinators.matchers :as m]
            [dialog-tool.skein.ui.tree-pane :refer [node-color-class]]
            [dialog-tool.skein.ui.common :as common]
            [dialog-tool.skein.session :as session]))

;; Guard condition tests

(deftest blank-command-guard-rejects-blank-inputs
  (testing "blank command is correctly rejected by string/blank? after normalize"
    (let [blank-inputs [" " "" "\t\n" "\r\n" "    "]
          blanks-checked? #(string/blank? (common/normalize-input %))]
      (is (every? blanks-checked? blank-inputs)))))

(deftest non-blank-command-is-allowed
  (testing "meaningful non-blank commands pass the guard"
    (let [good-inputs ["look" "wait for it" "n" "  hello world  "]
          non-blanks #(not (string/blank? (common/normalize-input %)))]
      (is (every? non-blanks good-inputs)))))

;; node-color-class tests

(deftest node-color-ok-with-new-descendant
  (testing "ok status with new descendant on-spine"
    (is (= "bg-warning/30 text-base-content"
           (node-color-class :ok :new true false))))
  (testing "ok status with new descendant off-spine"
    (is (= "bg-warning/20 text-base-content"
           (node-color-class :ok :new false false)))))

(deftest node-color-ok-with-error-descendant
  (testing "ok status with error descendant on-spine"
    (is (= "bg-error/30 text-base-content"
           (node-color-class :ok :error true false))))
  (testing "ok status with error descendant off-spine"
    (is (= "bg-error/20 text-base-content"
           (node-color-class :ok :error false false)))))

(deftest node-color-new-variants
  (testing "new status: active, on-spine, off-spine"
    (is (= "bg-warning text-warning-content" (node-color-class :new :new true true)))
    (is (= "bg-warning/80 text-warning-content" (node-color-class :new :new true false)))
    (is (= "bg-warning/40 text-warning-content" (node-color-class :new :new false false)))))

(deftest node-color-error-variants
  (testing "error status: active, on-spine, off-spine"
    (is (= "bg-error text-error-content" (node-color-class :error :error true true)))
    (is (= "bg-error/80 text-error-content" (node-color-class :error :error true false)))
    (is (= "bg-error/40 text-error-content" (node-color-class :error :error false false)))))

(deftest node-color-ok-variants
  (testing "ok status: active, on-spine, off-spine"
    (is (= "bg-primary text-primary-content" (node-color-class :ok :ok true true)))
    (is (= "bg-primary-content text-primary" (node-color-class :ok :ok true false)))
    (is (= "bg-neutral-content text-neutral" (node-color-class :ok :ok false false)))))

(deftest insert-parent-blank-command-is-silent
  (testing "blank command returns unchanged session - no visible feedback to user"
    (let [session {:tree {:knots {0 {:id 0} 1 {:id 1}}, :children {}}
                   :undo-stack []
                   :redo-stack []
                   :process-knot-id 0}
          [error _] (session/insert-parent! session 1 "")]
      ;; This is the BUG: blank command returns [nil unchanged-session],
      ;; modal dismisses silently, user sees nothing.
      (is (nil? error)))))
