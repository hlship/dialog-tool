(ns dialog-tool.skein.dynamic
  "Parses the dgdebug response for @dynamic into map of predicates, and makes it possible
  to present what changed between commands."
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
    (loop [fact-value value
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
           lines (next lines)]
      (let [line (first lines)]
        (if (soft-end? line)
          [(assoc-in output [:object-flags fact] (seq values)) lines]
          (let [object-names (string/split line #"\s+")
                values' (if (string/starts-with? line "#")
                          (into values object-names)
                               ;; Handle ugly case where an object name was split at a dash
                          (let [last-value (last values)
                                replacement-value (str last-value (first object-names))]
                            (-> values
                                pop
                                (conj replacement-value)
                                (into (rest object-names)))))]
            (recur values' (next lines))))))))

(defn- object-var
  [output lines]
  (let [fact (first lines)]
    (loop [values []
           lines (next lines)]
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
    (let [line (first lines)
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

(defn parse-predicates
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
(def ^:private location-pred "($ is $ $)")

(defn flatten-predicates
  "Flattens the predicates map into a set of predicates that are present. 
  
  :flags are global and per-object flags that are present (and have non-<unset> values).
  The predicate names have been \"flattened\" to replace \"$\" with the object name
  (for per-object flags).
  
  :vars are global and per-object variables as tuples of the predicate name and then the
  flattened predicate.
  
  In addition, the ($ has parent $) and ($ has relation $) predicates
  are merged into ($ is $ $), as a special case."
  [predicates]
  (let [{:keys [object-flags global-flags global-vars object-vars]} predicates
        parent-preds (get object-vars parent-pred)
        obj->relation (reduce (fn [m [k v]]
                                (assoc m k v))
                              {}
                              (get object-vars relation-pred))
        location-vars (reduce (fn [m [what where]]
                                (assoc m
                                       (flatten-pred location-pred what)
                                       (flatten-pred+ location-pred
                                                      what
                                                      (get obj->relation what "<unset>")
                                                      where)))
                              {}
                              parent-preds)
        object-tuples (for [[pred-name object+values] (dissoc object-vars parent-pred relation-pred)
                            [object-name object-value] object+values]
                        [(flatten-pred pred-name object-name)
                         (flatten-pred+ pred-name object-name object-value)])
        global-tuples (for [[pred-name object-value] global-vars]
                        [pred-name (flatten-pred pred-name object-value)])
        active-global-flags (keep (fn [[pred-name value]]
                                    (when (string/starts-with? value "on")
                                      pred-name))
                                  global-flags)
        active-object-flags (for [[pred-name object-names] object-flags
                                  object-name object-names]
                              (flatten-pred pred-name object-name))]
    {:flags (-> active-global-flags
                (concat active-object-flags)
                set)
     :vars (-> location-vars
               (into object-tuples)
               (into global-tuples))}))

(defn- unset?
  "Returns true if the string contains the fake value <unset>."
  [s]
  (and s
       (string/includes? s "<unset>")))

(defn diff-flattened
  "Analyzes the before and after predicates (from flatten-predicates) and returns a map of :added, :removed,
  and :changed sets.  :added and :removed are sets of predicates added or removed,
  :changes is a set of before/after predicates.
  
  Variables that change from <unset> to a value are treated as :added.
  Variables that change from a value to <unset> are treated as :removed."
  [before after]
  (let [before-flags (:flags before)
        after-flags (:flags after)
        added-flags (set/difference after-flags before-flags)
        removed-flags (set/difference before-flags after-flags)
        before-vars (:vars before)
        after-vars (:vars after)
        f (fn [m k]
            (let [before-val (get before-vars k)
                  after-val (get after-vars k)]
              (cond
                (= before-val after-val)
                m

                            ;; <unset> -> value is treated as added
                (and (unset? before-val)
                     (some? after-val))
                (update m :added conj after-val)

                            ;; value -> <unset> is treated as removed
                (and (some? before-val)
                     (unset? after-val))
                (update m :removed conj before-val)

                            ;; both have non-<unset> values
                (and (some? before-val)
                     (some? after-val))
                (update m :changed conj [before-val after-val])

                (some? before-val)
                (update m :removed conj before-val)

                :else
                (update m :added conj after-val))))
        all-keys (set/union (keys before-vars) (keys after-vars))]
    (reduce f
            {:added added-flags
             :removed removed-flags
             :changed #{}}
            all-keys)))
