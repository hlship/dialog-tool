(ns dialog-tool.skein.dynamic-test
  (:require [clojure.java.io :as io]
            [dialog-tool.skein.dynamic :as dynamic]
            [matcher-combinators.test :refer [match?]]
            [dialog-tool.skein.dynamic :refer [parse-predicates flatten-predicates diff-flattened]]
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
         (-> "dynamic-1.txt" file-contents parse-predicates))))

(deftest spot-checks
  (let [parsed (-> "dynamic-1.txt" file-contents parse-predicates)]
    (is (match? {:global-flags #{"(player can see)" "(sand-dancer is named)"}
                 :object-flags {"($ is traded)" ["#road-trips" "#last-day-of-high-school" "#your-shit-job"]}
                 :global-vars {"(previous room $)" "#crumbling-concrete"
                               "(remaining cigarettes $)" "6"
                               "(them refers to $)" "[#canned-oranges]"}
                 :object-vars {"($ number of trades $)" [["#sand-dancer" "3"]]
                               "($ has conversation queue $)" [["#sand-dancer" "[]"]]
                               "($ is recollected by $)" [["#final-choice" "[#sand-dancer]"]
                                                          ["#greet-sd" "[#sand-dancer #knock]"]
                                                          ["#about-sand-dancer" "[#sand-dancer #knock]"]
                                                          ["#path-selection" "[#sand-dancer #knock]"]
                                                          ["#start-sd-trade" "[#sand-dancer]"]
                                                          ["#final-not-sure" "[#sand-dancer #knock]"]
                                                          ["#about-spirit" "[#sand-dancer #knock]"]
                                                          ["#about-freedom" "[#sand-dancer #knock]"]]}}
                parsed))))

(deftest flatten-test
  ;; Spot check some of this
  (let [parsed (-> "dynamic-1.txt"
                   file-contents
                   parse-predicates)
        flattened (flatten-predicates parsed)]
    (is (match? {:flags (m/equals #{"(#pickup-truck is closed)"
                                    "(#road-trips is traded)"
                                    "(#flashlight is handled)"
                                    "(#cage is locked)"
                                    "(#blanket is handled)"
                                    "(#cage is closed)"
                                    "(#sand-dancer has traded)"
                                    "(#roof is visited)"
                                    "(#strength is noted useful)"
                                    "(#canned-oranges is handled)"
                                    "(#duct-tape is noted useful)"
                                    "(#wallet is closed)"
                                    "(#blanket is noted useful)"
                                    "(#crumbling-concrete is visited)"
                                    "(#wallet is handled)"
                                    "(#last-day-of-high-school is traded)"
                                    "(#pane-of-cracked-glass is closed)"
                                    "(#lighter is handled)"
                                    "(#glove-compartment is closed)"
                                    "(#strength is handled)"
                                    "(player can see)"
                                    "(#gas-can is closed)"
                                    "(#dust-covered-window is closed)"
                                    "(#your-shit-job is traded)"
                                    "(#emotional-baggage is handled)"
                                    "(#sand-dancers-offer has completed)"
                                    "(#storage-room is visited)"
                                    "(#gas-can is noted useful)"
                                    "(#photo is closed)"
                                    "(#radio is off)"
                                    "(#canned-oranges is noted useful)"
                                    "(#scent is noted useful)"
                                    "(#tiny-frosted-window is closed)"
                                    "(sand-dancer is named)"
                                    "(#sand-dancers-offer has started)"
                                    "(#scent is handled)"
                                    "(#flashlight is noted useful)"
                                    "(#gas-can is handled)"
                                    "(#sand-dancers-arrival has completed)"
                                    "(#middle-of-nowhere is visited)"
                                    "(#sand-dancers-arrival has started)"
                                    "(#drawer is closed)"}),
                 :vars (m/equals {"(#pane-of-cracked-glass is $ $)"
                                  "(#pane-of-cracked-glass is #in #crumbling-concrete)",
                                  "(#shelves is $ $)" "(#shelves is #in #storage-room)",
                                  "(#smell-of-gasoline is $ $)"
                                  "(#smell-of-gasoline is #in #control-center)",
                                  "(current room $)" "(current room #crumbling-concrete)",
                                  "(#rusted-barrels is $ $)"
                                  "(#rusted-barrels is #in #weed-strewn-rust)",
                                  "(#meeting-ocean is $ $)"
                                  "(#meeting-ocean is #in #emotional-baggage)",
                                  "(#canned-oranges is $ $)" "(#canned-oranges is #heldby #knock)",
                                  "(#desert-sand is $ $)" "(#desert-sand is #in #crumbling-concrete)",
                                  "(#radio is $ $)" "(#radio is #in #break-room)",
                                  "(#overturned-barrel is $ $)"
                                  "(#overturned-barrel is #in #weed-strewn-rust)",
                                  "(#sand-dancer is $ $)" "(#sand-dancer is #in #roof)",
                                  "(#final-choice is recollected by $)"
                                  "(#final-choice is recollected by [#sand-dancer])",
                                  "(#watching-family-guy is $ $)"
                                  "(#watching-family-guy is #in #emotional-baggage)",
                                  "(#metal-sheet is $ $)" "(#metal-sheet is #in #control-center)",
                                  "(#patches-of-mold is $ $)"
                                  "(#patches-of-mold is #in #staging-area)",
                                  "(#weeds is $ $)" "(#weeds is #in #weed-strewn-rust)",
                                  "(#about-spirit is recollected by $)"
                                  "(#about-spirit is recollected by [#sand-dancer #knock])",
                                  "(#about-sand-dancer is recollected by $)"
                                  "(#about-sand-dancer is recollected by [#sand-dancer #knock])",
                                  "(previous room $)" "(previous room #crumbling-concrete)",
                                  "(#wallet is $ $)" "(#wallet is #heldby #knock)",
                                  "(#flashlight is $ $)" "(#flashlight is #heldby #knock)",
                                  "(#jacket is $ $)" "(#jacket is #wornby #knock)",
                                  "(#emotional-baggage is $ $)"
                                  "(#emotional-baggage is #heldby #knock)",
                                  "(#leaking-pipe is $ $)" "(#leaking-pipe is #in #weed-strewn-rust)",
                                  "(#knock is $ $)" "(#knock is #in #crumbling-concrete)",
                                  "(#metal-desk is $ $)" "(#metal-desk is #in #staging-area)",
                                  "(#blanket is $ $)" "(#blanket is #heldby #knock)",
                                  "(#sunglasses is $ $)" "(#sunglasses is #wornby #coyote)",
                                  "(remaining cigarettes $)" "(remaining cigarettes 6)",
                                  "(#whiffs-of-gasoline is $ $)"
                                  "(#whiffs-of-gasoline is #in #middle-of-nowhere)",
                                  "(them refers to $)" "(them refers to [#canned-oranges])",
                                  "(#emergency-lights is $ $)"
                                  "(#emergency-lights is #in #storage-room)",
                                  "(#boarded-up-door is $ $)"
                                  "(#boarded-up-door is #in #crumbling-concrete)",
                                  "(#layers-of-sand is $ $)" "(#layers-of-sand is #in #storage-room)",
                                  "(#pickup-truck is $ $)" "(#pickup-truck is #in #middle-of-nowhere)",
                                  "(#rusty-can is $ $)" "(#rusty-can is #in #base-of-tower)",
                                  "(#shafts-of-light is $ $)"
                                  "(#shafts-of-light is #in #staging-area)",
                                  "(#receipt is $ $)" "(#receipt is #in #wallet)",
                                  "(#dial is $ $)" "(#dial is #partof #radio)",
                                  "(#rusted-key is $ $)" "(#rusted-key is #on #foremans-desk)",
                                  "(#drawer is $ $)" "(#drawer is #partof #metal-desk)",
                                  "(#cage is $ $)" "(#cage is #in #break-room)",
                                  "(#about-freedom is recollected by $)"
                                  "(#about-freedom is recollected by [#sand-dancer #knock])",
                                  "(#lighter is $ $)" "(#lighter is #heldby #knock)",
                                  "(#cans-of-food is $ $)" "(#cans-of-food is #on #shelves)",
                                  "(#girder is $ $)" "(#girder is #in #base-of-tower)",
                                  "(#skylight is $ $)" "(#skylight is #in #storage-room)",
                                  "(#bits-of-trash is $ $)" "(#bits-of-trash is #in #base-of-tower)",
                                  "(current visibility ceiling $)"
                                  "(current visibility ceiling #crumbling-concrete)",
                                  "(#greet-sd is recollected by $)"
                                  "(#greet-sd is recollected by [#sand-dancer #knock])",
                                  "(#poster is $ $)" "(#poster is #in #office)",
                                  "(#start-sd-trade is recollected by $)"
                                  "(#start-sd-trade is recollected by [#sand-dancer])",
                                  "(current player $)" "(current player #knock)",
                                  "(#cigarette is $ $)" "(#cigarette is #heldby #coyote)",
                                  "(#grandmas-stories is $ $)"
                                  "(#grandmas-stories is #in #emotional-baggage)",
                                  "(turns in current room $)" "(turns in current room 1)",
                                  "(#final-not-sure is recollected by $)"
                                  "(#final-not-sure is recollected by [#sand-dancer #knock])",
                                  "(#tables is $ $)" "(#tables is #in #break-room)",
                                  "(#tumbleweed is $ $)" "(#tumbleweed is #in #base-of-tower)",
                                  "(#electrical-tower is $ $)"
                                  "(#electrical-tower is #in #base-of-tower)",
                                  "(#crumbling-trash is $ $)" "(#crumbling-trash is #in #break-room)",
                                  "(#headlights is $ $)" "(#headlights is #partof #pickup-truck)",
                                  "(#sand-dancer has conversation queue $)"
                                  "(#sand-dancer has conversation queue [])",
                                  "(#piles-of-trash is $ $)"
                                  "(#piles-of-trash is #in #control-center)",
                                  "(last command was $)" "(last command was [s])",
                                  "(#tower is $ $)" "(#tower is #in #crumbling-concrete)",
                                  "(#cobwebs is $ $)" "(#cobwebs is #in #hole)",
                                  "(reported score is $)" "(reported score is 0)",
                                  "(#scrawny-weeds is $ $)" "(#scrawny-weeds is #in #base-of-tower)",
                                  "(#freedom is $ $)" "(#freedom is #in #roof)",
                                  "(#rabbit is $ $)" "(#rabbit is #in #burrow)",
                                  "(#sagebrush is $ $)" "(#sagebrush is #in #crumbling-concrete)",
                                  "(#can-opener is $ $)" "(#can-opener is #in #control-center)",
                                  "(#foremans-desk is $ $)" "(#foremans-desk is #in #office)",
                                  "(#panel is $ $)" "(#panel is #in #storage-room)",
                                  "(current actor $)" "(current actor #knock)",
                                  "(#strength is $ $)" "(#strength is #heldby #knock)",
                                  "(#withered-cactus is $ $)"
                                  "(#withered-cactus is #in #backtracking)",
                                  "(#newspapers is $ $)" "(#newspapers is #in #weed-strewn-rust)",
                                  "(#ladder is $ $)" "(#ladder is #in #storage-room)",
                                  "(#dust-covered-window is $ $)"
                                  "(#dust-covered-window is #in #office)",
                                  "(#red-warning-light is $ $)"
                                  "(#red-warning-light is #in #base-of-tower)",
                                  "(#ultrasound is $ $)" "(#ultrasound is #in #photo)",
                                  "(#sand-dancer number of trades $)"
                                  "(#sand-dancer number of trades 3)",
                                  "(#jade is $ $)" "(#jade is #in #pickup-truck)",
                                  "(current score $)" "(current score 0)",
                                  "(#denim-jacket is $ $)" "(#denim-jacket is #wornby #coyote)",
                                  "(#license is $ $)" "(#license is #in #wallet)",
                                  "(#glove-compartment is $ $)"
                                  "(#glove-compartment is #partof #pickup-truck)",
                                  "(#spirit is $ $)" "(#spirit is #heldby #knock)",
                                  "(#gas-can is $ $)" "(#gas-can is #heldby #knock)",
                                  "(#photo is $ $)" "(#photo is #in #wallet)",
                                  "(#holes-in-roof is $ $)" "(#holes-in-roof is #in #staging-area)",
                                  "(discussable quips $)" "(discussable quips [])",
                                  "(#hole is $ $)" "(#hole is #in #staging-area)",
                                  "(#path-selection is recollected by $)"
                                  "(#path-selection is recollected by [#sand-dancer #knock])",
                                  "(#saguaro is $ $)" "(#saguaro is #in #middle-of-nowhere)",
                                  "(#roots is $ $)" "(#roots is #in #burrow)",
                                  "(#guidebook is $ $)" "(#guidebook is #on #overturned-barrel)",
                                  "(#pack is $ $)" "(#pack is #in #glove-compartment)",
                                  "(#scent is $ $)" "(#scent is #heldby #knock)",
                                  "(#tire-tracks is $ $)" "(#tire-tracks is #in #middle-of-nowhere)",
                                  "(#lizards is $ $)" "(#lizards is #in #roof)",
                                  "(#tiny-frosted-window is $ $)"
                                  "(#tiny-frosted-window is #in #break-room)",
                                  "(#lizard is $ $)" "(#lizard is #in #middle-of-nowhere)"})}
                flattened))))

(deftest parse-when-global-var-wraps

  (let [predicates (-> "dynamic-global-var-wrap.txt"
                       file-contents
                       parse-predicates)]
    ;; In the debug output, this line has a word break after "#about-"
    (is (match? {:global-vars {"(discussable quips $)" "[#about-sand-dancer #about-lizards]"}}
                predicates))))

(deftest parse-when-object-flag-wraps

  (let [predicates (-> "dynamic-object-flag-wrap.txt"
                       file-contents
                       parse-predicates)]
    ;; In the debug output, this line has a word break after "#pane-of-"
    (is (match? {:object-flags {"($ is closed)" ["#drawer"
                                                 "#dust-covered-window"
                                                 "#glove-compartment"
                                                 "#photo"
                                                 "#pane-of-cracked-glass"
                                                 "#gas-can"
                                                 "#tiny-frosted-window"
                                                 "#cage"]}}
                predicates))))

(deftest diff-flattened-predicates
  (let [f #(-> % file-contents parse-predicates flatten-predicates)
        before (f "dynamic-before.txt")
        after (f "dynamic-after.txt")
        diff (diff-flattened before after)]
    (is (match?
         {:added (m/equals #{"(#duct-tape is noted useful)"})
          :removed (m/equals #{"(#duct-tape is hidden)"})
          :changed (m/equals
                    #{["(turns in current room 5)" "(turns in current room 6)"]
                      ["(last command was [light web])" "(last command was [x hole])"]
                      ["(player's it refers to #lighter)" "(player's it refers to #hole)"]})}
         diff))))

(deftest diff-handles-unset-as-add-or-remove
  (let [before {:flags #{}
                :vars {"(conversation partner $)" "(conversation partner #sand-dancer)"
                       "(current quip $)" "(current quip #greet)"}}
        after {:flags #{}
               :vars {"(player's it refers to $)" "(player's it refers to #lighter)"
                      "(current quip $)" "(current quip #farewell)"}}
        diff (diff-flattened before after)]
    (is (match?
         {:added (m/equals #{"(player's it refers to #lighter)"})
          :removed (m/equals #{"(conversation partner #sand-dancer)"})
          :changed (m/equals
                    #{["(current quip #greet)" "(current quip #farewell)"]})}
         diff))))

(deftest parse-object-value-with-split
  (let [parsed (->> "dynamic-split-dict-list.txt"
                    file-contents
                    parse-predicates)]
    (is (match?
          {:object-vars
           {"($ has conversation queue $)"
            [["#bartender" "[[#postponed-obligatory #play-billiards]]"]]}}
          parsed))))
