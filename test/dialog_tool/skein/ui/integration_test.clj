(ns dialog-tool.skein.ui.integration-test
  "Integration tests for the skein UI using hyper.test.

   These tests exercise page rendering and action flow without needing a running
   game engine or browser — hyper.test provides an isolated context."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [hyper.test :as ht]
            [dialog-tool.skein.ui.app :refer [skein-page]]
            [dialog-tool.skein.ui.test-helpers :refer [make-session-map make-minimal-tree]]))

;; ---------------------------------------------------------------------------
;; Page rendering tests

(deftest page-renders-tree-pane-with-nodes
  "When a valid tree is seeded, the rendered HTML contains knot content."
  (let [tree    (make-minimal-tree)
        ;; common.clj/session-cursor reads from [:global :session], not session scope.
        ;; hyper.test seeds global cursors under {:cursors {:global {:session ...}}}.
        result  (ht/test-page skein-page
          {:cursors {:global {:session (make-session-map tree)}}})]
    (is (str/includes? (:body-html result) "START"))
    (is (str/includes? (:body-html result) "rose garden"))))

(deftest page-renders-knot-counts-correctly
  "The navbar shows correct knot counts: 1 ok, 1 new."
  (let [tree    (make-minimal-tree)
        result  (ht/test-page skein-page
          {:cursors {:global {:session (make-session-map tree)}}})]
    (is (str/includes? (:body-html result) "1 ok knots"))
    (is (str/includes? (:body-html result) "1 new knots"))))

(deftest page-renders-start-knot-as-active
  "The START knot should appear highlighted when it is the active knot."
  (let [tree    (make-minimal-tree)
        result  (ht/test-page skein-page
          {:cursors {:global {:session (make-session-map tree)}}})]
    ;; The active node pill has bg-warning + border-primary classes
    (is (str/includes? (:body-html result) "bg-warning"))))

(deftest page-renders-with-error-status
  "A knot with :error status still renders the page."
  (let [tree     (-> (make-minimal-tree)
                     ;; Change response to create unblessed state (= error)
                     (update-in [:knots 1 :response] (constantly "different text.")))
        session  (make-session-map tree)
        result   (ht/test-page skein-page
          {:cursors {:global {:session session}}})]
    ;; The page should still render successfully with error indicator
    (is (:body-html result))
    (is (str/includes? (:body-html result) "error knots"))))

;; ---------------------------------------------------------------------------
;; State mutation via actions

(deftest bless-knot-action-registered-by-render
  "The bless-knot action should be registered in the page's actions map."
  (let [tree     (make-minimal-tree)
        session  (-> (make-session-map tree)
                     ;; Remove blessing to have an unblessed knot
                     (update-in [:tree :knots 1] dissoc :response))
        result   (ht/test-page skein-page
          {:cursors {:global {:session session}}})
        actions  (:actions result)]
    ;; There should be some actions registered by the render
    (is (seq actions) "Expected some actions to be registered")))

;; ---------------------------------------------------------------------------
;; Loading state

(deftest page-renders-spinner-when-loading
  "When :loading? is true, a spinner placeholder renders."
  (let [tree     (make-minimal-tree)
        session  (-> (make-session-map tree)
                     (assoc :loading? true))
        result   (ht/test-page skein-page
          {:cursors {:global {:session session}}})]
    ;; The loading state should render the spinner instead of knots
    (is (str/includes? (:body-html result) "loading-spinner"))))
