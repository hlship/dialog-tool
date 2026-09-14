(ns dialog-tool.skein.ui.tree-pane-test
  (:require [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test :refer [match?]]
            [matcher-combinators.matchers :as m]
            [dialog-tool.skein.ui.tree-pane :refer [node-color-class]]
            [dialog-tool.helpers :refer [make-tree]])
  (:import (java.io StringWriter PrintWriter)))

;; ---------------------------------------------------------------------------
;; Test helpers

(defn- make-session
  "Creates a minimal session map suitable for testing."
  ([tree]
   {:tree tree
    :undo-stack []
    :redo-stack []
    :process-knot-id 0})
  ([]
   (make-session (make-tree))))

;; ---------------------------------------------------------------------------
;; node-color-class tests

(deftest node-color-class-ok-active
  (testing "active knot with ok status uses primary bg"
    (is (= "bg-primary text-primary-content"
           (node-color-class :ok :ok true true)))))

(deftest node-color-class-ok-on-spine
  (testing "on-spine knot with ok status uses primary-content"
    (is (= "bg-primary-content text-primary"
           (node-color-class :ok :ok true false)))))

(deftest node-color-class-ok-off-spine
  (testing "off-spine knot with ok status uses neutral-content"
    (is (= "bg-neutral-content text-neutral"
           (node-color-class :ok :ok false false)))))

(deftest node-color-class-new-active
  (testing "active new knot gets full-warning bg"
    (is (= "bg-warning text-warning-content"
           (node-color-class :new :new true true)))))

(deftest node-color-class-new-on-spine
  (testing "on-spine new knot gets 80% opacity warning"
    (is (= "bg-warning/80 text-warning-content"
           (node-color-class :new :new true false)))))

(deftest node-color-class-new-off-spine
  (testing "off-spine new knot gets 40% opacity warning"
    (is (= "bg-warning/40 text-warning-content"
           (node-color-class :new :new false false)))))

(deftest node-color-class-error-active
  (testing "active error knot gets full-error bg"
    (is (= "bg-error text-error-content"
           (node-color-class :error :error true true)))))

(deftest node-color-class-error-on-spine
  (testing "on-spine error knot gets 80% opacity error"
    (is (= "bg-error/80 text-error-content"
           (node-color-class :error :error true false)))))

(deftest node-color-class-error-off-spine
  (testing "off-spine error knot gets 40% opacity error"
    (is (= "bg-error/40 text-error-content"
           (node-color-class :error :error false false)))))

(deftest node-color-class-ok-with-bad-descendant-on-spine
  (testing "ok knot with descendant errors on-spine gets tinted bg"
    (is (= "bg-error/30 text-base-content"
           (node-color-class :ok :error true false)))))

(deftest node-color-class-ok-with-bad-descendant-off-spine
  (testing "ok knot with descendant errors off-spine gets lighter tint"
    (is (= "bg-error/20 text-base-content"
           (node-color-class :ok :error false false)))))

(deftest node-color-class-ok-with-new-descendant-on-spine
  (testing "ok knot with descendant new on-spine gets warning tint"
    (is (= "bg-warning/30 text-base-content"
           (node-color-class :ok :new true false)))))
