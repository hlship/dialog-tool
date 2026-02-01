(ns dialog-tool.skein.dyn.parser
  "Parses the dgdebug response for @dynamic into a Clojure datastructure."
  (:require [clojure.string :as string]))

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
  ;; TODO: For vars that contain lists, the list may word wrap
  [output lines]
  (let [line (first lines)
        [_ fact value] (re-matches #"(?ix)
    \s*
    (\(.+\)) # fact
    \s+
    (.*) # value
    \s*"
                                   line)]
    ;; TODO: Special case for lists?
    [(assoc-in output [:global-vars fact] value)
     (rest lines)]))


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

(defn parse
  [response]
  (->> response
       string/split-lines
       ;; The first line is ">@dynamic", then "GLOBAL FLAGS"
       (drop 2)
       (map string/trim)
       ;; TODO: Could we "unwrap" wrapped lines to simplify logic?
       (parse* :global-flags {})))
