(ns dialog-tool.skein.dynamic-test
  (:require [clojure.java.io :as io]
            [matcher-combinators.test :refer [match?]]
            [dialog-tool.skein.dynamic :refer [parse-predicates flatten-predicates]]
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

(deftest flatten-test
  ;; Spot check some of this
  (let [parsed    (-> "dynamic-1.txt"
                      file-contents
                      parse-predicates)
        flattened (flatten-predicates parsed)]
    (is (set? flattened))
    (is (match? ["(#about-freedom is recollected by [#sand-dancer #knock])"
                 "(#about-sand-dancer is recollected by [#sand-dancer #knock])"
                 "(#about-spirit is recollected by [#sand-dancer #knock])"
                 "(#bits-of-trash is #in #base-of-tower)"
                 "(#blanket is #heldby #knock)"
                 "(#blanket is handled)"
                 "(#blanket is noted useful)"
                 "(#boarded-up-door is #in #crumbling-concrete)"
                 "(#cage is #in #break-room)"
                 "(#cage is closed)"
                 "(#cage is locked)"
                 "(#can-opener is #in #control-center)"
                 "(#canned-oranges is #heldby #knock)"
                 "(#canned-oranges is handled)"
                 "(#canned-oranges is noted useful)"
                 "(#cans-of-food is #on #shelves)"
                 "(#cigarette is #heldby #coyote)"
                 "(#cobwebs is #in #hole)"
                 "(#crumbling-concrete is visited)"
                 "(#crumbling-trash is #in #break-room)"
                 "(#denim-jacket is #wornby #coyote)"
                 "(#desert-sand is #in #crumbling-concrete)"
                 "(#dial is #partof #radio)"
                 "(#drawer is #partof #metal-desk)"
                 "(#drawer is closed)"
                 "(#duct-tape is noted useful)"
                 "(#dust-covered-window is #in #office)"
                 "(#dust-covered-window is closed)"
                 "(#electrical-tower is #in #base-of-tower)"
                 "(#emergency-lights is #in #storage-room)"
                 "(#emotional-baggage is #heldby #knock)"
                 "(#emotional-baggage is handled)"
                 "(#final-choice is recollected by [#sand-dancer])"
                 "(#final-not-sure is recollected by [#sand-dancer #knock])"
                 "(#flashlight is #heldby #knock)"
                 "(#flashlight is handled)"
                 "(#flashlight is noted useful)"
                 "(#foremans-desk is #in #office)"
                 "(#freedom is #in #roof)"
                 "(#gas-can is #heldby #knock)"
                 "(#gas-can is closed)"
                 "(#gas-can is handled)"
                 "(#gas-can is noted useful)"
                 "(#girder is #in #base-of-tower)"
                 "(#glove-compartment is #partof #pickup-truck)"
                 "(#glove-compartment is closed)"
                 "(#grandmas-stories is #in #emotional-baggage)"
                 "(#greet-sd is recollected by [#sand-dancer #knock])"
                 "(#guidebook is #on #overturned-barrel)"
                 "(#headlights is #partof #pickup-truck)"
                 "(#hole is #in #staging-area)"
                 "(#holes-in-roof is #in #staging-area)"
                 "(#jacket is #wornby #knock)"
                 "(#jade is #in #pickup-truck)"
                 "(#knock is #in #crumbling-concrete)"
                 "(#ladder is #in #storage-room)"
                 "(#last-day-of-high-school is traded)"
                 "(#layers-of-sand is #in #storage-room)"
                 "(#leaking-pipe is #in #weed-strewn-rust)"
                 "(#license is #in #wallet)"
                 "(#lighter is #heldby #knock)"
                 "(#lighter is handled)"
                 "(#lizard is #in #middle-of-nowhere)"
                 "(#lizards is #in #roof)"
                 "(#meeting-ocean is #in #emotional-baggage)"
                 "(#metal-desk is #in #staging-area)"
                 "(#metal-sheet is #in #control-center)"
                 "(#middle-of-nowhere is visited)"
                 "(#newspapers is #in #weed-strewn-rust)"
                 "(#overturned-barrel is #in #weed-strewn-rust)"
                 "(#pack is #in #glove-compartment)"
                 "(#pane-of-cracked-glass is #in #crumbling-concrete)"
                 "(#pane-of-cracked-glass is closed)"
                 "(#panel is #in #storage-room)"
                 "(#patches-of-mold is #in #staging-area)"
                 "(#path-selection is recollected by [#sand-dancer #knock])"
                 "(#photo is #in #wallet)"
                 "(#photo is closed)"
                 "(#pickup-truck is #in #middle-of-nowhere)"
                 "(#pickup-truck is closed)"
                 "(#piles-of-trash is #in #control-center)"
                 "(#poster is #in #office)"
                 "(#rabbit is #in #burrow)"
                 "(#radio is #in #break-room)"
                 "(#radio is off)"
                 "(#receipt is #in #wallet)"
                 "(#red-warning-light is #in #base-of-tower)"
                 "(#road-trips is traded)"
                 "(#roof is visited)"
                 "(#roots is #in #burrow)"
                 "(#rusted-barrels is #in #weed-strewn-rust)"
                 "(#rusted-key is #on #foremans-desk)"
                 "(#rusty-can is #in #base-of-tower)"
                 "(#sagebrush is #in #crumbling-concrete)"
                 "(#saguaro is #in #middle-of-nowhere)"
                 "(#sand-dancer has conversation queue [])"
                 "(#sand-dancer has traded)"
                 "(#sand-dancer is #in #roof)"
                 "(#sand-dancer number of trades 3)"
                 "(#sand-dancers-arrival has completed)"
                 "(#sand-dancers-arrival has started)"
                 "(#sand-dancers-offer has completed)"
                 "(#sand-dancers-offer has started)"
                 "(#scent is #heldby #knock)"
                 "(#scent is handled)"
                 "(#scent is noted useful)"
                 "(#scrawny-weeds is #in #base-of-tower)"
                 "(#shafts-of-light is #in #staging-area)"
                 "(#shelves is #in #storage-room)"
                 "(#skylight is #in #storage-room)"
                 "(#smell-of-gasoline is #in #control-center)"
                 "(#spirit is #heldby #knock)"
                 "(#start-sd-trade is recollected by [#sand-dancer])"
                 "(#storage-room is visited)"
                 "(#strength is #heldby #knock)"
                 "(#strength is handled)"
                 "(#strength is noted useful)"
                 "(#sunglasses is #wornby #coyote)"
                 "(#tables is #in #break-room)"
                 "(#tiny-frosted-window is #in #break-room)"
                 "(#tiny-frosted-window is closed)"
                 "(#tire-tracks is #in #middle-of-nowhere)"
                 "(#tower is #in #crumbling-concrete)"
                 "(#tumbleweed is #in #base-of-tower)"
                 "(#ultrasound is #in #photo)"
                 "(#wallet is #heldby #knock)"
                 "(#wallet is closed)"
                 "(#wallet is handled)"
                 "(#watching-family-guy is #in #emotional-baggage)"
                 "(#weeds is #in #weed-strewn-rust)"
                 "(#whiffs-of-gasoline is #in #middle-of-nowhere)"
                 "(#withered-cactus is #in #backtracking)"
                 "(#your-shit-job is traded)"
                 "(conversation partner <unset>)"
                 "(current actor #knock)"
                 "(current node <unset>)"
                 "(current player #knock)"
                 "(current quip <unset>)"
                 "(current room #crumbling-concrete)"
                 "(current score 0)"
                 "(current visibility ceiling #crumbling-concrete)"
                 "(deferred commandline <unset>)"
                 "(discussable quips [])"
                 "(grandparent quip <unset>)"
                 "(her refers to <unset>)"
                 "(him refers to <unset>)"
                 "(implicit action is <unset>)"
                 "(last command was [s])"
                 "(narrator's it refers to <unset>)"
                 "(player can see)"
                 "(player's it refers to <unset>)"
                 "(previous node <unset>)"
                 "(previous quip <unset>)"
                 "(previous room #crumbling-concrete)"
                 "(pursuit direction <unset>)"
                 "(pursuit duration <unset>)"
                 "(remaining cigarettes 6)"
                 "(reported score is 0)"
                 "(sand-dancer is named)"
                 "(them refers to [#canned-oranges])"
                 "(turns in current room 1)"
                 "(visible flotsam <unset>)"]
                (sort flattened)))))


(deftest parse-when-global-var-wraps

  (let [predicates (-> "dynamic-global-var-wrap.txt"
                       file-contents
                       parse-predicates)]
    ;; In the debug output, this line has a word break after "#about-"
    (is (match? {:global-vars {"(discussable quips $)" "[#about-sand-dancer #about-lizards]",}}
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
