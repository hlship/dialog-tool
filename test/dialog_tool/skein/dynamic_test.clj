(ns dialog-tool.skein.dynamic-test
  (:require [clojure.java.io :as io]
            [matcher-combinators.test :refer [match?]]
            [dialog-tool.skein.dynamic :refer [parse->predicates flatten-predicates diff-flattened]]
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
         (-> "dynamic-1.txt" file-contents parse->predicates))))

(deftest spot-checks
  (let [parsed (-> "dynamic-1.txt" file-contents parse->predicates)]
    (is (match? {:global-flags {"(inhibiting next tick)" "off"
                                "(sand-dancer is named)" "on (changed)"}
                 :object-flags {"($ is exposed)" nil
                                "($ is traded)"  ["#road-trips" "#last-day-of-high-school" "#your-shit-job"]}
                 :global-vars  {"(previous room $)"        "#crumbling-concrete"
                                "(remaining cigarettes $)" "6"
                                "(them refers to $)"       "[#canned-oranges]"
                                "(her refers to $)"        "<unset>"}
                 :object-vars  {"($ number of trades $)"  [["#sand-dancer" "3"]]
                                "($ is tuned to $)"       nil
                                "($ is recollected by $)" [["#final-choice" "[#sand-dancer]"]
                                                           ["#greet-sd" "[#sand-dancer #knock]"]
                                                           ["#about-sand-dancer" "[#sand-dancer #knock]"]
                                                           ["#path-selection" "[#sand-dancer #knock]"]
                                                           ["#start-sd-trade" "[#sand-dancer]"]
                                                           ["#final-not-sure" "[#sand-dancer #knock]"]
                                                           ["#about-spirit" "[#sand-dancer #knock]"]
                                                           ["#about-freedom" "[#sand-dancer #knock]"]]}}
                parsed))))

;; Will need more tests later when we have good examples of word wrapping of @dynamic output

(deftest flatten-test
  ;; Spot check some of this
  (let [parsed    (-> "dynamic-1.txt"
                      file-contents
                      parse->predicates)
        flattened (flatten-predicates parsed)]
    (is (identical? (:global-flags flattened)
                    (:global-flags parsed)))
    (is (match? {:global-vars  (m/embeds ["(implicit action is <unset>)"
                                          "(last command was [s])"
                                          "(narrator's it refers to <unset>)"
                                          "(player's it refers to <unset>)"
                                          "(previous node <unset>)"
                                          "(previous quip <unset>)"
                                          "(previous room #crumbling-concrete)"])
                 :object-vars  (m/embeds ["(#about-spirit is recollected by [#sand-dancer #knock])"
                                          "(#bits-of-trash is #in #base-of-tower)"
                                          "(#blanket is #heldby #knock)"
                                          "(#boarded-up-door is #in #crumbling-concrete)"
                                          "(#sand-dancer number of trades 3)"])
                 :object-flags (m/embeds ["(#sand-dancer has traded)"
                                          "(#sand-dancers-arrival has completed)"
                                          "(#sand-dancers-arrival has started)"
                                          "(#sand-dancers-offer has completed)"
                                          "(#sand-dancers-offer has started)"
                                          "(#scent is handled)"
                                          "(#last-day-of-high-school is traded)"])}
                flattened))))

(deftest flattened-diff
  (let [before (-> "dynamic-before.txt"
                   file-contents
                   parse->predicates
                   flatten-predicates)
        after  (-> "dynamic-after.txt"
                   file-contents
                   parse->predicates
                   flatten-predicates)
        diff   (diff-flattened before after)]
    (is (match?
          {:object-flags (m/equals [[:removed "(#duct-tape is hidden)"]
                                    [:added "(#duct-tape is noted useful)"]])
           :global-vars  (m/equals [[:removed "(last command was [light web])"]
                                    [:added "(last command was [x hole])"]
                                    [:added "(player's it refers to #hole)"]
                                    [:removed "(player's it refers to #lighter)"]
                                    [:removed "(turns in current room 5)"]
                                    [:added "(turns in current room 6)"]])}
          diff))))

(deftest flattened-diff-global-flags

  (let [before {:global-flags {"(does not change)" "false"
                               "(about to change)" "true"}}
        after  {:global-flags {"(does not change)" "false"
                               "(about to change)" "false (changed)"}}]
    (is (match?
          {:global-flags (m/equals {"(about to change)" "false (changed)"})}
          (diff-flattened before after)))))


(deftest parse-when-global-var-wraps

  (let [predicates (-> "dynamic-global-var-wrap.txt"
                       file-contents
                       parse->predicates)]
    ;; In the debug output, this line has a word break after "#about-"
    (is (match? {:global-vars {"(discussable quips $)" "[#about-sand-dancer #about-lizards]",}}
                predicates))))
