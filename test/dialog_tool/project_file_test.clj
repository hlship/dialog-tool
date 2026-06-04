(ns dialog-tool.project-file-test
  (:require [clojure.test :refer [deftest is]]
            [matcher-combinators.test :refer [match?]]
            [dialog-tool.project-file :as pf]))

(deftest filters-paths-by-target
  (let [project (pf/read-project "test-fixtures/target-filter")]
    (is (match?
          ["test-fixtures/target-filter/src/always.dg"
           "test-fixtures/target-filter/src/never.whatsit.dg" ;; Yes, even this
           "test-fixtures/target-filter/src/sometimes.aa.dg"
           "test-fixtures/target-filter/src/sometimes.zblorb.dg"]
          (pf/expand-sources project))
        "includes all .dg files if no target")

    (is (match?
          ["test-fixtures/target-filter/src/always.dg"
           "test-fixtures/target-filter/src/sometimes.zblorb.dg"]
          (pf/expand-sources project {:target :zblorb}))
        "includes non-target and matching target files when target :zblorb provided")

    (is (match?
          ["test-fixtures/target-filter/src/always.dg"
           "test-fixtures/target-filter/src/sometimes.aa.dg"]
          (pf/expand-sources project {:target :aa}))
        "includes non-target and matching target files when target :aa provided")))
