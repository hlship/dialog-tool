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
          (let [object-names (string/split line #"\s+")
                values'      (if (string/starts-with? line "#")
                               (into values object-names)
                               ;; Handle ugly case where an object name was split at a dash
                               (let [last-value        (last values)
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

(defn flatten-predicates
  "Flattens the predicates map into a set of predicates that are present.  For global flags, this
  is any flags that are \"on\".  
  
  In addition, the ($ has parent $) and ($ has relation $) predicates
  are merged into ($ is $ $), as a special case."
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
                            parent-preds)
        active-flags   (keep (fn [[pred-name value]]
                               (when (string/starts-with? value "on")
                                 pred-name))
                             global-flags)]
    (reduce into #{}
            [active-flags
             (map (fn [[pred-name value]]
                    (flatten-pred pred-name value))
                  global-vars)
             (mapcat (fn [[pred-name objects]]
                       (map #(flatten-pred pred-name %) objects))
                     object-flags)
             (->> (dissoc object-vars parent-pred relation-pred)
                  (mapcat (fn [[pred-name obj+value-pairs]]
                            (map (fn [[obj value]]
                                   (flatten-pred+ pred-name obj value))
                                 obj+value-pairs))))
             location-preds])))
