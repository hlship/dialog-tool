(ns dialog-tool.skein.ui.tree-pane-test
  (:require [clojure.test :refer [deftest is]]
            [dialog-tool.skein.ui.tree-pane :as tree-pane]))

;; ---------------------------------------------------------------------------
;; node-color-class (private — accessed via var alias)

(def node-color-class #'tree-pane/node-color-class)

(deftest new-not-active-not-spine
  (is (= "bg-warning/40 text-warning-content"
         (node-color-class :new nil false false))))

(deftest new-not-active-on-spine
  (is (= "bg-warning/80 text-warning-content"
         (node-color-class :new nil true false))))

(deftest new-active-not-spine
  (is (= "bg-warning text-warning-content"
         (node-color-class :new nil false true))))

(deftest error-not-active-not-spine
  (is (= "bg-error/40 text-error-content"
         (node-color-class :error nil false false))))

(deftest error-not-active-on-spine
  (is (= "bg-error/80 text-error-content"
         (node-color-class :error nil true false))))

(deftest error-active-not-spine
  (is (= "bg-error text-error-content"
         (node-color-class :error nil false true))))

(deftest ok-with-error-descendant-not-active-not-spine
  (is (= "bg-error/20 text-base-content"
         (node-color-class :ok :error false false))))

(deftest ok-with-error-descendant-not-active-on-spine
  (is (= "bg-error/30 text-base-content"
         (node-color-class :ok :error true false))))

(deftest ok-with-error-descendant-active-not-spine
  (is (= "bg-error/50 text-base-content"
         (node-color-class :ok :error false true))))

(deftest ok-with-new-descendant-not-active-not-spine
  (is (= "bg-warning/20 text-base-content"
         (node-color-class :ok :new false false))))

(deftest ok-with-new-descendant-not-active-on-spine
  (is (= "bg-warning/30 text-base-content"
         (node-color-class :ok :new true false))))

(deftest ok-with-new-descendant-active-not-spine
  (is (= "bg-warning/50 text-base-content"
         (node-color-class :ok :new false true))))

(deftest ok-no-descendant-not-active-not-spine
  (is (= "bg-neutral-content text-neutral"
         (node-color-class :ok nil false false))))

(deftest ok-no-descendant-not-active-on-spine
  (is (= "bg-primary-content text-primary"
         (node-color-class :ok nil true false))))

(deftest ok-no-descendant-active-not-spine
  (is (= "bg-primary text-primary-content"
         (node-color-class :ok nil false true))))
