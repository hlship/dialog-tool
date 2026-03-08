(ns dialog-tool.skein.trace-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [dialog-tool.skein.trace :as trace]
            [matcher-combinators.test :refer [match?]]
            [matcher-combinators.matchers :as m]))

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
  (is (= [] (trace/build-tree []))))

(deftest build-tree-flat-siblings
  (testing "all nodes at the same level become siblings with no children"
    (let [lines [{:level 3 :type :query :predicate "A" :source "a.dg:1"}
                 {:level 3 :type :query :predicate "B" :source "b.dg:2"}
                 {:level 3 :type :found :predicate "C" :source "c.dg:3"}]
          tree (trace/build-tree lines)]
      (is (= 3 (count tree)))
      (is (match? [{:predicate "A" :children []}
                   {:predicate "B" :children []}
                   {:predicate "C" :children []}]
                  tree)))))

(deftest build-tree-parent-child
  (testing "a deeper-level line becomes a child of the preceding line"
    (let [lines [{:level 3 :type :enter :predicate "parent" :source "a.dg:1"}
                 {:level 4 :type :query :predicate "child" :source "a.dg:2"}]
          tree (trace/build-tree lines)]
      (is (= 1 (count tree)))
      (is (match? [{:predicate "parent"
                    :children [{:predicate "child"
                                :children []}]}]
                  tree)))))

(deftest build-tree-parent-with-children-then-sibling
  (testing "level drop back to parent level starts a new sibling"
    (let [lines [{:level 3 :type :enter :predicate "first" :source "a.dg:1"}
                 {:level 4 :type :query :predicate "child-of-first" :source "a.dg:2"}
                 {:level 3 :type :found :predicate "second" :source "a.dg:3"}]
          tree (trace/build-tree lines)]
      (is (= 2 (count tree)))
      (is (match? [{:predicate "first"
                    :children [{:predicate "child-of-first"}]}
                   {:predicate "second"
                    :children []}]
                  tree)))))

(deftest build-tree-deep-nesting
  (testing "three levels of nesting"
    (let [lines [{:level 5 :type :enter :predicate "grandparent" :source "a.dg:1"}
                 {:level 6 :type :enter :predicate "parent" :source "a.dg:2"}
                 {:level 7 :type :query :predicate "child" :source "a.dg:3"}
                 {:level 7 :type :found :predicate "child-found" :source "a.dg:4"}
                 {:level 6 :type :found :predicate "parent-found" :source "a.dg:5"}
                 {:level 5 :type :found :predicate "grandparent-found" :source "a.dg:6"}]
          tree (trace/build-tree lines)]
      (is (= 2 (count tree)))
      (is (match? [{:predicate "grandparent"
                    :children [{:predicate "parent"
                                :children [{:predicate "child" :children []}
                                           {:predicate "child-found" :children []}]}
                               {:predicate "parent-found"
                                :children []}]}
                   {:predicate "grandparent-found"
                    :children []}]
                  tree)))))

(deftest build-tree-real-trace
  (testing "parsing the full trace.txt produces a tree with expected structure"
    (let [raw (file-contents "trace.txt")
          lines (trace/parse-trace raw)
          tree (trace/build-tree lines)]
      ;; Should have root-level nodes
      (is (pos? (count tree)))
      ;; The total node count should match the number of parsed trace lines
      (is (= (count lines) (trace/count-nodes tree)))
      ;; The trace starts mid-stream at level 3, so those become root nodes
      ;; (the minimum level across the full trace is 1)
      (is (match? {:level 3
                   :type :found
                   :predicate "(get input [i])"}
                  (first tree)))
      ;; Level 1 nodes appear later and are also roots
      (is (some #(= 1 (:level %)) tree)))))

;;; search-tree

(defn- simple-tree
  "Builds a small tree for search tests:
   root (stdlib.dg)
     ├── child-a (penny.dg) - matches 'penny'
     │     └── grandchild (stdlib.dg)
     └── child-b (tc.dg)"
  []
  [{:level 3 :type :enter :predicate "(root action)" :source "stdlib.dg:100"
    :children [{:level 4 :type :query :predicate "(find penny)" :source "penny.dg:10"
                :children [{:level 5 :type :found :predicate "(found it)" :source "stdlib.dg:200"
                            :children []}]}
               {:level 4 :type :query :predicate "(conversation partner $)" :source "tc.dg:345"
                :children []}]}])

(deftest search-tree-blank-returns-unchanged
  (let [tree (simple-tree)]
    (is (= tree (trace/search-tree tree "")))
    (is (= tree (trace/search-tree tree nil)))
    (is (= tree (trace/search-tree tree "   ")))))

(deftest search-tree-predicate-match
  (testing "direct match on predicate marks :match? and :has-match?"
    (let [result (trace/search-tree (simple-tree) "penny")]
      ;; child-a matches directly
      (is (match? [{:match? false
                    :has-match? true
                    :children [{:match? true
                                :has-match? true
                                :predicate "(find penny)"}
                               {:match? false
                                :has-match? false}]}]
                  result)))))

(deftest search-tree-source-match
  (testing "match on source file"
    (let [result (trace/search-tree (simple-tree) "tc.dg")]
      (is (match? [{:match? false
                    :has-match? true
                    :children [{:match? false :has-match? false}
                               {:match? true :has-match? true
                                :source "tc.dg:345"}]}]
                  result)))))

(deftest search-tree-case-insensitive
  (testing "search is case-insensitive"
    (let [result (trace/search-tree (simple-tree) "PENNY")]
      (is (match? [{:has-match? true
                    :children [{:match? true
                                :predicate "(find penny)"}
                               {}]}]
                  result)))))

(deftest search-tree-has-match-propagates-up
  (testing ":has-match? propagates from grandchild through parent to root"
    ;; Search for "found it" which is only in the grandchild
    (let [result (trace/search-tree (simple-tree) "found it")]
      ;; grandchild matches directly
      ;; child-a doesn't match directly but has-match? is true
      ;; root doesn't match directly but has-match? is true
      (is (match? [{:match? false
                    :has-match? true
                    :children [{:match? false
                                :has-match? true
                                :children [{:match? true
                                            :has-match? true}]}
                               {:match? false
                                :has-match? false}]}]
                  result)))))

(deftest search-tree-no-match
  (testing "no match marks everything false"
    (let [result (trace/search-tree (simple-tree) "nonexistent")]
      (is (match? [{:match? false
                    :has-match? false
                    :children [{:match? false
                                :has-match? false
                                :children [{:match? false
                                            :has-match? false}]}
                               {:match? false
                                :has-match? false}]}]
                  result)))))

;;; count-nodes

(deftest count-nodes-empty
  (is (= 0 (trace/count-nodes []))))

(deftest count-nodes-flat
  (is (= 3 (trace/count-nodes [{:children []} {:children []} {:children []}]))))

(deftest count-nodes-nested
  (is (= 4 (trace/count-nodes (simple-tree)))))

(deftest count-nodes-real-trace
  (testing "count matches the number of parsed trace lines from trace.txt"
    (let [raw (file-contents "trace.txt")
          lines (trace/parse-trace raw)
          tree (trace/build-tree lines)]
      (is (= (count lines) (trace/count-nodes tree))))))
