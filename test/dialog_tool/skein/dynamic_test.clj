(ns dialog-tool.skein.dynamic-test
  (:require [clojure.java.io :as io]
            [matcher-combinators.test :refer [match?]]
            [dialog-tool.skein.dynamic :refer [parse  diff]]
            [matcher-combinators.matchers :as m]
            [clojure.test :refer [deftest is]]
            [clojure.edn :as edn]))

(defn- file-contents
  [file-name]
  (if-let [url (io/resource file-name)]
    (slurp url)
    (throw (ex-info (str file-name " not found.")
                    {}))))

(deftest basic-parse
  ;; This is more of a canary test
  (is (= (-> "dynamic-1.edn" file-contents edn/read-string)
         (-> "dynamic-1.txt" file-contents parse))))

(deftest parse-when-global-var-wraps

  (let [predicates (-> "dynamic-global-var-wrap.txt"
                       file-contents
                       parse)]
    ;; In the debug output, this line has a word break after "#about-"
    (is (match? {:vars {"(discussable quips $)" "(discussable quips [#about-sand-dancer #about-lizards])"}}
                predicates))))

(deftest parse-when-object-flag-wraps

  (let [predicates (-> "dynamic-object-flag-wrap.txt"
                       file-contents
                       parse)]
    ;; In the debug output, this line has a word break after "#pane-of-"
    (is (match? {:flags (m/embeds #{"(#pane-of-cracked-glass is closed)"})}
                predicates))))

(deftest diff-flattened-predicates
  (let [f      #(-> % file-contents parse)
        before (f "dynamic-before.txt")
        after (f "dynamic-after.txt")
        diff (diff before after)]
    (is (match?
         {:added (m/equals #{"(#duct-tape is noted useful)"})
          :removed (m/equals #{"(#duct-tape is hidden)"})
          :changed (m/equals
                    #{["(turns in current room 5)" "(turns in current room 6)"]
                      ["(last command was [light web])" "(last command was [x hole])"]
                      ["(player's it refers to #lighter)" "(player's it refers to #hole)"]})}
         diff))))

(deftest diff-handles-missing-as-add-or-remove
  (let [before {:flags #{}
                :vars {"(conversation partner $)" "(conversation partner #sand-dancer)"
                       "(current quip $)" "(current quip #greet)"}}
        after {:flags #{}
               :vars {"(player's it refers to $)" "(player's it refers to #lighter)"
                      "(current quip $)" "(current quip #farewell)"}}
        diff (diff before after)]
    (is (match?
         {:added (m/equals #{"(player's it refers to #lighter)"})
          :removed (m/equals #{"(conversation partner #sand-dancer)"})
          :changed (m/equals
                    #{["(current quip #greet)" "(current quip #farewell)"]})}
         diff))))

(deftest parse-object-value-with-split
  (let [parsed (->> "dynamic-split-dict-list.txt"
                    file-contents
                    parse)]
    (is (match?
          {:vars
           {"(#bartender has conversation queue $)" "(#bartender has conversation queue [[#postponed-obligatory #play-billiards]])"}}
          parsed))))

(deftest parse-when-list-word-wrapped
  ;; This data was extracted from the "tree of life" example in dialog-extensions
  ;; This also checks the more recent code that strips out the ANSI sequences
  (let [predicates (-> "dynamic-list-wrapped.txt"
                       file-contents
                       parse)]
    (is (match?
          ;; The old parser got confused because (AFAIK) a word break inside a list has a single space
          ;; before the new value on the subsequent line and this confused the logic, 
          ;; and a nil predicate was added as a global var.
          ;; dbdebug may do things differently in the GLOBAL VARIABLES section than in the PER-OBJECT VARIABLES
          ;; section.
          {:vars {nil m/absent}}
          predicates))))
