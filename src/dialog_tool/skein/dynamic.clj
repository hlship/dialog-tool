(ns dialog-tool.skein.dynamic
  "Parses the dgdebug response for @dynamic into map of predicates, and makes it possible
  to present what changed between commands.
  
  The noun 'dynamic' refers to the "
  (:require [clojure.set :as set]
            [clojure.string :as string]))

;; Assuming that word breaks will *NOT* occur mid-object name

(defn- soft-end?
  [line]
  (or (string/blank? line)
      (string/starts-with? line "(")))

(defn- global-flag
  [output line]
  (let [[_ fact value] (re-matches #"(?ix)
    \s*
    (\(.+\)) # fact
    \s+
    (.*) # value
    \s*"
                                   line)]
    (assoc-in output [:global-flags fact] value)))

(defn- global-var
  [output lines]
  (let [line (first lines)
        [_ fact value] (re-matches #"(?ix)
    \s*
    (\(.+\)) # fact
    \s+
    (.*) # start of values (may span lines)"
                                   line)]
    (loop [fact-value      value
           remaining-lines (next lines)]
      (let [line (first remaining-lines)]
        (if (soft-end? line)
          [(assoc-in output [:global-vars fact] fact-value)
           remaining-lines]
          ;; It may break mid-word at a dash, or between words.
          ;; This may need to be revisited if a dictionary words list
          ;; (such as '(last command was $)') is split across lines. 
          (recur (str fact-value
                      ;; Add a space between object names, but not when a name is split at a dash
                      (when (string/starts-with? line "#") " ")
                      line)
                 (next remaining-lines)))))))


(defn- object-flag
  [output lines]
  (let [fact (first lines)]
    (loop [values []
           lines  (next lines)]
      (let [line (first lines)]
        (if (soft-end? line)
          [(assoc-in output [:object-flags fact] (seq values)) lines]
          (recur (into values (string/split line #"\s+"))
                 (next lines)))))))

(defn- object-var
  [output lines]
  (let [fact (first lines)]
    (loop [values []
           lines  (next lines)]
      (let [line (first lines)]
        (if (soft-end? line)
          [(assoc-in output [:object-vars fact] (seq values))
           lines]
          (let [obj+val (string/split line #"\s+" 2)]
            (recur (conj values obj+val)
                   (next lines))))))))

(defn- parse*
  [state output lines]
  (if-not lines
    output
    (let [line   (first lines)
          lines' (next lines)]
      (cond

        (string/blank? line)
        (recur state output lines')

        :else
        (case state
          :global-flags
          (if (= line "PER-OBJECT FLAGS")
            (recur :object-flags output lines')
            (recur state
                   (global-flag output line)
                   lines'))

          :object-flags
          (if (= line "GLOBAL VARIABLES")
            (recur :global-vars output lines')
            (let [[output' remaining-lines] (object-flag output lines)]
              (recur state output' remaining-lines)))

          :global-vars
          (if (= line "PER-OBJECT VARIABLES")
            (recur :object-vars output lines')
            (let [[output' remaining-lines] (global-var output lines)]
              (recur state output' remaining-lines)))

          ;; Final state
          :object-vars
          (let [[output' remaining-lines] (object-var output lines)]
            (recur state output' remaining-lines)))))))

(defn parse->predicates
  "Parse the captured response for the command \"@dynamic\" into a predicates map that can be further processed."
  [response]
  (->> response
       string/split-lines
       ;; The first line is ">@dynamic", then "GLOBAL FLAGS"
       (drop 2)
       (map string/trim)
       ;; TODO: Could we "unwrap" wrapped lines to simplify logic?
       (parse* :global-flags {})))

(defn- flatten-pred
  [pred-name value]
  (assert (some? pred-name))
  (assert (some? value))
  (string/replace-first pred-name "$" value))

(defn- flatten-pred+
  [pred-name & values]
  (assert (some? pred-name))
  (assert (seq values))
  (assert (every? some? values))
  (reduce #(flatten-pred %1 %2)
          pred-name
          values))

(def ^:private parent-pred "($ has parent $)")
(def ^:private relation-pred "($ has relation $)")

(defn flatten-predicates
  "Flattens the predicates map into a form that can be used to identify added/removed/changed
  predicates and flags."
  [predicates]
  (let [{:keys [object-flags global-flags global-vars object-vars]} predicates
        parent-preds   (get object-vars parent-pred)
        obj->relation  (reduce (fn [m [k v]]
                                 (assoc m k v))
                               {}
                               (get object-vars relation-pred))
        location-preds (map (fn [[what where]]
                              (flatten-pred+ "($ is $ $)"
                                             what
                                             (get obj->relation what "<unset>")
                                             where))
                            parent-preds)]
    {:global-flags global-flags                             ;; Fine like it is
     ;; The next few we do strict, not lazy, because it's really hard to debug otherwise.
     :global-vars  (mapv (fn [[pred-name value]]
                           (flatten-pred pred-name value))
                         global-vars)
     :object-flags (->> (mapcat (fn [[pred-name objects]]
                                  (map #(flatten-pred pred-name %) objects))
                                object-flags)
                        vec)
     :object-vars  (->> (dissoc object-vars parent-pred relation-pred)
                        (mapcat (fn [[pred-name obj+value-pairs]]
                                  (map (fn [[obj value]]
                                         (flatten-pred+ pred-name obj value))
                                       obj+value-pairs)))
                        vec
                        (into location-preds))}))

(defn- compare-tuples
  [left right]
  (let [result (compare (second left) (second right))]
    (if (zero? result)
      (if (= :removed (first left)) -1 1)
      result)))

(defn- diff*
  [k before after]
  (let [before-preds  (set (get before k))
        after-preds   (set (get after k))
        added-preds   (set/difference after-preds before-preds)
        removed-preds (set/difference before-preds after-preds)
        added         (map #(vector :added %) added-preds)
        removed       (map #(vector :removed %) removed-preds)]
    (->> (concat added removed)
         (sort compare-tuples))))

(defn diff-flattened
  "Compares two flattened predicates maps and returns a map of differences. 
  
  :global-flags is a sorted map the global flags with any unchanged values removed (maybe empty).
  :global-vars, :object-vars, and :object-flags are sorted seq of tuples; each tuple
  is of the form [:removed predicate-name] or [:added predicate-name], sorted first by predicate name, 
  then :removed before :added."
  [before after]
  (let [before-flags (:global-flags before)
        after-flags  (:global-flags after)]
    {:global-vars  (diff* :global-vars before after)
     :object-vars  (diff* :object-vars before after)
     :object-flags (diff* :object-flags before after)
     ;; unlike predicates, global flags always have a value
     :global-flags (reduce-kv (fn [m k v]
                                (if (= (get before-flags k) v)
                                  m
                                  (assoc m k v)))
                              (sorted-map)
                              after-flags)}))
