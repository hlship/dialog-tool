(ns dialog-tool.skein.dynamic
  "Parses the dgdebug response for @dynamic into map of predicates, and makes it possible
  to present what changed between commands."
  (:require [clojure.set :as set]
            [clojure.string :as string]))

(defn- maybe-conj
  [coll v]
  (if (some? v)
    (conj coll v)
    coll))

(defn- combine-lines
  [prior-line new-line]
  (str prior-line
       ;; Little worried about square brackets, but in the meantime:
       (when-not (string/ends-with? prior-line "-")
         " ")
       new-line))

(defn- pre-parse
  "Trims lines and combines unindented lines with prior line."
  [lines]
  (loop [result []
         prior-line nil
         [line & remaining-lines] lines]
    (cond
      (nil? line)
      (maybe-conj result prior-line)

      (string/blank? line)
      (recur (-> result
                 (maybe-conj prior-line)
                 (conj ""))
             nil
             remaining-lines)

      ;; sections are always preceded by a blank line so don't need to worry about prior-line
      (re-matches #"[A-Z \-]+" line)
      (recur (conj result line) nil remaining-lines)

      ;; Indented line: finish prior line if necessary, start a new line as prior
      (string/starts-with? line " ")
      (recur (maybe-conj result prior-line) (string/trim line) remaining-lines)

      :else
      ;; Not indented
      (recur result (combine-lines prior-line line) remaining-lines))))

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

(defn- flatten-predicates
  "Flattens the predicates map into a set of predicates that are present. 
  
  :flags are global and per-object flags that are set.
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
        active-object-flags (for [[pred-name object-names] object-flags
                                  object-name object-names]
                              (flatten-pred pred-name object-name))]
    {:flags (-> global-flags
                (concat active-object-flags)
                set)
     :vars (-> location-vars
               (into object-tuples)
               (into global-tuples))}))

(defn- soft-end?
  [line]
  (or (string/blank? line)
      (string/starts-with? line "(")))

(defn- global-flag
  [output line]
  (let [[_ fact value] (re-matches #"(?ix)
    (\(.+\)) # fact
    \s+
    (.*) # value"
                                   line)]
    (if (string/starts-with? value "on")
      (update output :global-flags conj fact)
      output)))

(defn- global-var
  [output line]
  (let [[_ fact value] (re-matches #"(?ix)
    (\(.+\)) # fact
    \s+
    (.*)"
                                   line)]
    (if-not (= value "<unset>")
      (assoc-in output [:global-vars fact] value)
      output)))

(defn- object-flag
  [output lines]
  (let [[fact value & more-lines] lines]
    (if (soft-end? value)
      ;; Per-object flag with no values.
      [output (rest lines)]
      [(assoc-in output [:object-flags fact] (string/split value #"\s+")) more-lines])))

(defn- object-var
  [output lines]
  (let [fact (first lines)]
    ;; Each predicate line is followed by some number of object/value lines.
    (loop [values []
           lines (next lines)]
      (let [line (first lines)
            end? (soft-end? line)]
        (cond
          (and end? (seq values))
          [(assoc-in output [:object-vars fact] values)
           lines]

          end?
          [output lines]

          :else
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
            (recur state (global-var output line) lines'))

          ;; Final state
          :object-vars
          (let [[output' remaining-lines] (object-var output lines)]
            (recur state output' remaining-lines)))))))

(defn- strip-ansi
  "Removes ANSI escape sequences from a string."
  [s]
  (string/replace s #"\x1b\[[0-9;]*m" ""))

(defn parse
  "Parse the captured response for the command \"@dynamic\" into a predicates map that can be further processed.
  
  :flags are global and per-object flags that are set.
  The predicate names have been \"flattened\" to replace \"$\" with the object name
  (for per-object flags).
  
  :vars are global and per-object variables as tuples of the predicate name and then the
  flattened predicate.
  
  In addition, the ($ has parent $) and ($ has relation $) predicates
  are merged into ($ is $ $), as a special case."
  [response]
  (->> response
       strip-ansi
       string/split-lines
       ;; The first line is ">@dynamic", then "GLOBAL FLAGS"
       (drop 2)
       pre-parse
       (parse* :global-flags {:global-flags #{}})
       flatten-predicates))

(defn diff
  "Analyzes the before and after predicates (from flatten-predicates) and returns a map of :added, :removed,
  and :changed sets.  :added and :removed are sets of predicates added or removed,
  :changes is a set of before/after tuples."
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

                (and (some? before-val)
                     (some? after-val))
                (update m :changed conj [before-val after-val])

                (some? before-val)
                (update m :removed conj before-val)

                :else
                (update m :added conj after-val))))
        all-keys (set/union (-> before-vars keys set)
                            (-> after-vars keys set))]
    (reduce f
            {:added added-flags
             :removed removed-flags
             :changed #{}}
            all-keys)))
