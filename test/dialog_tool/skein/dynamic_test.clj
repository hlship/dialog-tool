(ns dialog-tool.skein.dynamic-test
  (:require [clojure.java.io :as io]
            [matcher-combinators.test :refer [match?]]
            [dialog-tool.skein.dynamic :refer [parse flatten-predicates]]
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

(deftest spot-checks
  (let [parsed (-> "dynamic-1.txt" file-contents parse)]
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

;; Will need later tests when we have good examples of word wrapping of @dynamic output

(comment
  (-> "dynamic-1.txt"
      file-contents
      parse
      flatten-predicates)


  ;
  )


(deftest flatten-test
  ;; Spot check some of this
  (let [parsed    (-> "dynamic-1.txt"
                      file-contents
                      parse)
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
