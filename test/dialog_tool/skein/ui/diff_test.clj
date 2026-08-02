(ns dialog-tool.skein.ui.diff-test
  (:require [clojure.test :refer [deftest is]]
            [dialog-tool.skein.ui.diff :as diff]))

;; ---------------------------------------------------------------------------
;; nil-input behavior (fully predictable)

(deftest nil-old-all-added
  (is (= [{:type :added, :value "new text"}]
         (diff/diff-text nil "new text"))))

(deftest nil-old-empty-string
  (is (= [{:type :added, :value ""}]
         (diff/diff-text nil ""))))

(deftest nil-new-all-unchanged-test
  (is (= [{:type :unchanged, :value "old text"}]
         (diff/diff-text "old text" nil))))

(deftest nil-new-empty-string-test
  (is (= [{:type :unchanged, :value ""}]
         (diff/diff-text "" nil))))

(deftest both-nil-added-nil-value
  (is (= [{:type :added, :value nil}]
         (diff/diff-text nil nil))))

;; ---------------------------------------------------------------------------
;; actual diff behavior

(deftest diff-same-type-contiguous-blocks
  "Identical text returns a single block (no fragmentation)."
  (let [text "the quick brown fox jumps over the lazy dog"
        result (diff/diff-text text text)]
    (is (= 1 (count result)))
    (is (= :unchanged (-> result first :type)))))

(deftest diff-partial-change-no-adjacent-same-type
  "Adjacent blocks should never share the same type."
  (let [old "the quick brown fox"
        new "the red brown fox"]
    (let [result (diff/diff-text old new)]
      (doseq [[i block] (map-indexed vector result)
              :when (< i (dec (count result)))]
        (let [next-block (nth result (inc i))]
          (is (not= (:type block) (:type next-block))
              (str "Adjacent blocks at index " i " have same type")))))))

