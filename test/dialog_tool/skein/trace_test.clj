(ns dialog-tool.skein.trace-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [dialog-tool.skein.trace :as trace]
            [matcher-combinators.test :refer [match?]]))

(defn- file-contents [resource-name]
  (slurp (io/resource resource-name)))

;;; parse-trace

(deftest parse-trace-basic-types
  (testing "ENTER line"
    (is (match? [{:level 3
                  :type :enter
                  :predicate "(parse commandline [i] with choices [])"
                  :source "../lib/dialog/stdlib.dg:5767"}]
                (trace/parse-trace
                  "| 3 ENTER (parse commandline [i] with choices []) ../lib/dialog/stdlib.dg:5767"))))

  (testing "QUERY line"
    (is (match? [{:level 3
                  :type :query
                  :predicate "(save undo 1)"
                  :source "../lib/dialog/stdlib.dg:5769"}]
                (trace/parse-trace
                  "| 3 QUERY (save undo 1) ../lib/dialog/stdlib.dg:5769"))))

  (testing "FOUND line"
    (is (match? [{:level 5
                  :type :found
                  :predicate "(current player #player)"
                  :source "../lib/dialog/stdlib.dg:5803"}]
                (trace/parse-trace
                  "| 5 FOUND (current player #player) ../lib/dialog/stdlib.dg:5803"))))

  (testing "NOW line"
    (is (match? [{:level 5
                  :type :now
                  :predicate "(current actor #player)"
                  :source "../lib/dialog/stdlib.dg:5804"}]
                (trace/parse-trace
                  "| 5 NOW (current actor #player) ../lib/dialog/stdlib.dg:5804")))))

(deftest parse-trace-negated-now
  (testing "NOW with ~ negation"
    (is (match? [{:level 3
                  :type :now
                  :predicate "~(allowing parse errors)"
                  :source "../lib/dialog/stdlib.dg:5782"}]
                (trace/parse-trace
                  "| 3 NOW ~(allowing parse errors) ../lib/dialog/stdlib.dg:5782"))))

  (testing "NOW with ~ and $ variable"
    (is (match? [{:level 9
                  :type :now
                  :predicate "~($ has spoken this tick)"
                  :source "../lib/ext/tc.dg:348"}]
                (trace/parse-trace
                  "| 9 NOW ~($ has spoken this tick) ../lib/ext/tc.dg:348")))))

(deftest parse-trace-multi-query
  (testing "QUERY with * prefix"
    (is (match? [{:level 5
                  :type :query
                  :predicate "*(understand [i] as $)"
                  :source "../lib/dialog/stdlib.dg:5835"}]
                (trace/parse-trace
                  "| 5 QUERY *(understand [i] as $) ../lib/dialog/stdlib.dg:5835")))))

(deftest parse-trace-double-digit-level
  (testing "level 10"
    (is (match? [{:level 10 :type :enter}]
                (trace/parse-trace
                  "|10 ENTER (action [inventory] requires $ to be present) ../lib/dialog/stdlib.dg:3518"))))

  (testing "level 12"
    (is (match? [{:level 12 :type :query}]
                (trace/parse-trace
                  "|12 QUERY (#office has parent $) ../lib/dialog/stdlib.dg:456")))))

(deftest parse-trace-filters-non-trace-lines
  (testing "game text, prompts, and blank lines are filtered out"
    (let [input (str "> i\n"
                     "| 3 FOUND (get input [i]) ../lib/dialog/stdlib.dg:4888\n"
                     "You have no possessions.\n"
                     "\n"
                     "| 3 QUERY (nonempty []) ../lib/dialog/stdlib.dg:5775\n"
                     ">")]
      (is (= 2 (count (trace/parse-trace input))))
      (is (match? [{:type :found :predicate "(get input [i])"}
                   {:type :query :predicate "(nonempty [])"}]
                  (trace/parse-trace input))))))

(deftest parse-trace-empty-input
  (is (= [] (vec (trace/parse-trace ""))))
  (is (= [] (vec (trace/parse-trace "no trace lines here\njust game text")))))

(deftest parse-trace-with-ansi-codes
  (testing "ANSI escape sequences are stripped before parsing"
    (is (match? [{:level 3
                  :type :found
                  :predicate "(get input [i])"
                  :source "../lib/dialog/stdlib.dg:4888"}]
                (trace/parse-trace
                  "| 3 FOUND \u001b[1m(get input [i])\u001b[0m ../lib/dialog/stdlib.dg:4888"))))

  (testing "ANSI reset at start of line"
    (is (match? [{:level 5 :type :query}]
                (trace/parse-trace
                  "\u001b[0m| 5 QUERY (save undo 1) ../lib/dialog/stdlib.dg:5769"))))

  (testing "multiple ANSI codes interspersed"
    (is (match? [{:level 10 :type :enter}]
                (trace/parse-trace
                  "\u001b[0m|10 ENTER \u001b[1m(action [inventory] requires $ to be present)\u001b[0m ../lib/dialog/stdlib.dg:3518")))))

(deftest parse-trace-with-trailing-whitespace
  (testing "trailing spaces are trimmed"
    (is (match? [{:level 3
                  :type :found
                  :predicate "(get input [i])"
                  :source "../lib/dialog/stdlib.dg:4888"}]
                (trace/parse-trace
                  "| 3 FOUND (get input [i]) ../lib/dialog/stdlib.dg:4888   "))))

  (testing "trailing tabs and spaces"
    (is (match? [{:level 5 :type :now}]
                (trace/parse-trace
                  "| 5 NOW (current actor #player) ../lib/dialog/stdlib.dg:5804\t  ")))))

;;; build-tree

(deftest build-tree-empty
  (is (= {0 {:id 0 :children []}} (trace/build-tree []))))

(deftest build-tree-flat-siblings
  (testing "all nodes at the same level become siblings with no children"
    (let [lines [{:level 3 :type :query :predicate "A" :source "a.dg:1"}
                 {:level 3 :type :query :predicate "B" :source "b.dg:2"}
                 {:level 3 :type :found :predicate "C" :source "c.dg:3"}]
          nodes (trace/build-tree lines)]
      (is (= 3 (trace/count-nodes nodes)))
      (let [root (get nodes 0)]
        (is (= 3 (count (:children root))))
        (is (match? {:predicate "A" :children []} (get nodes (nth (:children root) 0))))
        (is (match? {:predicate "B" :children []} (get nodes (nth (:children root) 1))))
        (is (match? {:predicate "C" :children []} (get nodes (nth (:children root) 2))))))))

(deftest build-tree-parent-child
  (testing "a deeper-level line becomes a child of the preceding line"
    (let [lines [{:level 3 :type :enter :predicate "parent" :source "a.dg:1"}
                 {:level 4 :type :query :predicate "child" :source "a.dg:2"}]
          nodes (trace/build-tree lines)
          root (get nodes 0)]
      (is (= 1 (count (:children root))))
      (let [parent-id (first (:children root))
            parent (get nodes parent-id)]
        (is (match? {:predicate "parent"} parent))
        (is (= 1 (count (:children parent))))
        (let [child (get nodes (first (:children parent)))]
          (is (match? {:predicate "child" :children []} child)))))))

(deftest build-tree-parent-with-children-then-sibling
  (testing "level drop back to parent level starts a new sibling"
    (let [lines [{:level 3 :type :enter :predicate "first" :source "a.dg:1"}
                 {:level 4 :type :query :predicate "child-of-first" :source "a.dg:2"}
                 {:level 3 :type :found :predicate "second" :source "a.dg:3"}]
          nodes (trace/build-tree lines)
          root (get nodes 0)]
      (is (= 2 (count (:children root))))
      (let [first-node (get nodes (nth (:children root) 0))
            second-node (get nodes (nth (:children root) 1))]
        (is (match? {:predicate "first"} first-node))
        (is (= 1 (count (:children first-node))))
        (is (match? {:predicate "child-of-first"} (get nodes (first (:children first-node)))))
        (is (match? {:predicate "second" :children []} second-node))))))

(deftest build-tree-deep-nesting
  (testing "three levels of nesting"
    (let [lines [{:level 5 :type :enter :predicate "grandparent" :source "a.dg:1"}
                 {:level 6 :type :enter :predicate "parent" :source "a.dg:2"}
                 {:level 7 :type :query :predicate "child" :source "a.dg:3"}
                 {:level 7 :type :found :predicate "child-found" :source "a.dg:4"}
                 {:level 6 :type :found :predicate "parent-found" :source "a.dg:5"}
                 {:level 5 :type :found :predicate "grandparent-found" :source "a.dg:6"}]
          nodes (trace/build-tree lines)
          root (get nodes 0)]
      (is (= 2 (count (:children root))))
      (let [gp (get nodes (nth (:children root) 0))
            gp-found (get nodes (nth (:children root) 1))]
        (is (match? {:predicate "grandparent"} gp))
        (is (match? {:predicate "grandparent-found" :children []} gp-found))
        ;; grandparent has two children: parent and parent-found
        (is (= 2 (count (:children gp))))
        (let [parent (get nodes (nth (:children gp) 0))
              parent-found (get nodes (nth (:children gp) 1))]
          (is (match? {:predicate "parent"} parent))
          (is (match? {:predicate "parent-found" :children []} parent-found))
          ;; parent has two children: child and child-found
          (is (= 2 (count (:children parent))))
          (is (match? {:predicate "child" :children []} (get nodes (nth (:children parent) 0))))
          (is (match? {:predicate "child-found" :children []} (get nodes (nth (:children parent) 1)))))))))

(deftest build-tree-real-trace
  (testing "parsing the full trace.txt produces a tree with expected structure"
    (let [raw (file-contents "trace.txt")
          lines (trace/parse-trace raw)
          nodes (trace/build-tree lines)
          root (get nodes 0)]
      ;; Should have root-level nodes
      (is (pos? (count (:children root))))
      ;; The total node count should match the number of parsed trace lines
      (is (= (count lines) (trace/count-nodes nodes)))
      ;; The trace starts mid-stream at level 3, so those become root nodes
      ;; (the minimum level across the full trace is 1)
      (let [first-child (get nodes (first (:children root)))]
        (is (match? {:level 3
                     :type :found
                     :predicate "(get input [i])"}
                    first-child)))
      ;; Level 1 nodes appear later and are also roots (children of invisible root)
      (is (some #(= 1 (:level (get nodes %))) (:children root))))))

;;; search-tree

(defn- simple-tree
  "Builds a small flat node map for search tests:
   invisible root (id 0)
     └── root (id 1, stdlib.dg)
           ├── child-a (id 2, penny.dg) - matches 'penny'
           │     └── grandchild (id 3, stdlib.dg)
           └── child-b (id 4, tc.dg)"
  []
  {0 {:id 0 :children [1]}
   1 {:id 1 :level 3 :type :enter :predicate "(root action)" :source "stdlib.dg:100"
      :children [2 4]}
   2 {:id 2 :level 4 :type :query :predicate "(find penny)" :source "penny.dg:10"
      :children [3]}
   3 {:id 3 :level 5 :type :found :predicate "(found it)" :source "stdlib.dg:200"
      :children []}
   4 {:id 4 :level 4 :type :query :predicate "(conversation partner $)" :source "tc.dg:345"
      :children []}})

(deftest search-tree-blank-returns-unchanged
  (let [nodes (simple-tree)]
    (is (= nodes (trace/search-tree nodes "")))
    (is (= nodes (trace/search-tree nodes nil)))
    (is (= nodes (trace/search-tree nodes "   ")))))

(deftest search-tree-predicate-match
  (testing "direct match on predicate marks :match? and :has-match?"
    (let [result (trace/search-tree (simple-tree) "penny")]
      ;; root has-match (descendant matches)
      (is (match? {:match? false :has-match? true} (get result 1)))
      ;; child-a matches directly
      (is (match? {:match? true :has-match? true :predicate "(find penny)"} (get result 2)))
      ;; child-b does not match
      (is (match? {:match? false :has-match? false} (get result 4))))))

(deftest search-tree-source-match
  (testing "match on source file"
    (let [result (trace/search-tree (simple-tree) "tc.dg")]
      ;; root has-match (descendant matches)
      (is (match? {:match? false :has-match? true} (get result 1)))
      ;; child-a does not match
      (is (match? {:match? false :has-match? false} (get result 2)))
      ;; child-b matches on source
      (is (match? {:match? true :has-match? true :source "tc.dg:345"} (get result 4))))))

(deftest search-tree-case-insensitive
  (testing "search is case-insensitive"
    (let [result (trace/search-tree (simple-tree) "PENNY")]
      (is (match? {:has-match? true} (get result 1)))
      (is (match? {:match? true :predicate "(find penny)"} (get result 2))))))

(deftest search-tree-has-match-propagates-up
  (testing ":has-match? propagates from grandchild through parent to root"
    ;; Search for "found it" which is only in the grandchild (id 3)
    (let [result (trace/search-tree (simple-tree) "found it")]
      ;; grandchild matches directly
      (is (match? {:match? true :has-match? true} (get result 3)))
      ;; child-a doesn't match directly but has-match? is true (ancestor of match)
      (is (match? {:match? false :has-match? true} (get result 2)))
      ;; root doesn't match directly but has-match? is true
      (is (match? {:match? false :has-match? true} (get result 1)))
      ;; child-b doesn't match at all
      (is (match? {:match? false :has-match? false} (get result 4))))))

(deftest search-tree-no-match
  (testing "no match marks everything false"
    (let [result (trace/search-tree (simple-tree) "nonexistent")]
      (is (match? {:match? false :has-match? false} (get result 1)))
      (is (match? {:match? false :has-match? false} (get result 2)))
      (is (match? {:match? false :has-match? false} (get result 3)))
      (is (match? {:match? false :has-match? false} (get result 4))))))

;;; build-tree IDs

(deftest build-tree-assigns-unique-ids
  (testing "each node gets a unique numeric :id"
    (let [lines [{:level 3 :type :enter :predicate "A" :source "a.dg:1"}
                 {:level 4 :type :query :predicate "B" :source "b.dg:2"}
                 {:level 4 :type :found :predicate "C" :source "c.dg:3"}
                 {:level 3 :type :query :predicate "D" :source "d.dg:4"}]
          nodes (trace/build-tree lines)
          ;; All IDs except the invisible root (0)
          visible-ids (disj (set (keys nodes)) 0)]
      ;; All visible IDs should be positive integers
      (is (every? pos-int? visible-ids))
      ;; Should have 4 visible nodes
      (is (= 4 (count visible-ids)))
      ;; Each node's :id matches its key in the map
      (doseq [[id node] (dissoc nodes 0)]
        (is (= id (:id node)))))))

;;; get-node

(deftest get-node-by-id
  (let [lines [{:level 3 :type :enter :predicate "parent" :source "a.dg:1"}
               {:level 4 :type :query :predicate "child" :source "a.dg:2"}
               {:level 4 :type :found :predicate "sibling" :source "a.dg:3"}]
        nodes (trace/build-tree lines)
        root (get nodes 0)
        parent-id (first (:children root))]
    (testing "finds node by id"
      (is (match? {:predicate "parent"} (trace/get-node nodes parent-id))))
    (testing "finds child by id"
      (let [child-id (first (:children (get nodes parent-id)))]
        (is (match? {:predicate "child"} (trace/get-node nodes child-id)))))
    (testing "returns nil for non-existent id"
      (is (nil? (trace/get-node nodes 99999))))))

;;; find-first-match

(deftest find-first-match-returns-first-matching-id
  (let [nodes (trace/search-tree (simple-tree) "penny")]
    ;; "find penny" is the first direct match
    (is (some? (trace/find-first-match nodes)))
    (let [match-id (trace/find-first-match nodes)
          node (trace/get-node nodes match-id)]
      (is (match? {:predicate "(find penny)" :match? true} node)))))

(deftest find-first-match-returns-nil-when-no-match
  (let [nodes (trace/search-tree (simple-tree) "nonexistent")]
    (is (nil? (trace/find-first-match nodes)))))

(deftest find-first-match-blank-search
  (let [nodes (simple-tree)]
    ;; No :match? flags set
    (is (nil? (trace/find-first-match nodes)))))

;;; count-nodes

(deftest count-nodes-empty
  (is (= 0 (trace/count-nodes {0 {:id 0 :children []}}))))

(deftest count-nodes-flat
  (is (= 3 (trace/count-nodes {0 {:id 0 :children [1 2 3]}
                                1 {:id 1 :children []}
                                2 {:id 2 :children []}
                                3 {:id 3 :children []}}))))

(deftest count-nodes-nested
  ;; simple-tree has root (invisible) + 4 visible nodes
  (is (= 4 (trace/count-nodes (simple-tree)))))

(deftest count-nodes-real-trace
  (testing "count matches the number of parsed trace lines from trace.txt"
    (let [raw (file-contents "trace.txt")
          lines (trace/parse-trace raw)
          nodes (trace/build-tree lines)]
      (is (= (count lines) (trace/count-nodes nodes))))))
