(ns dialog-tool.skein.trace
  "Parses Dialog debugger trace output into a hierarchical tree structure.
   
   Trace output lines have the format:
     |N TYPE predicate source:line
   
   Where:
   - | identifies the line as trace output
   - N is the numeric nesting level
   - TYPE is ENTER, QUERY, FOUND, or NOW
   - predicate is the Dialog predicate being traced
   - source:line is the source file and line number"
  (:require [clojure.string :as string]))

(def ^:private ansi-pattern
  "Matches ANSI CSI SGR escape sequences."
  #"\u001b\[[0-9;]*m")

(defn- parse-trace-line
  "Parses a single trace line into a map, or nil if it doesn't match.
   Strips ANSI escape sequences and trims whitespace before matching,
   since the debugger process runs in a PTY."
  [line]
  (let [cleaned (-> line
                    (string/replace ansi-pattern "")
                    string/trim)]
    (when-let [[_ level type predicate source]
               (re-matches #"\|\s*(\d+)\s+(ENTER|QUERY|FOUND|NOW)\s+(.+?)\s+(\S+:\d+)" cleaned)]
      {:level (parse-long level)
       :type (keyword (string/lower-case type))
       :predicate predicate
       :source source})))

(defn parse-trace
  "Extracts and parses trace lines from a raw response string.
   Returns a sequence of parsed trace line maps."
  [raw-response]
  (->> (string/split-lines raw-response)
       (keep parse-trace-line)))

(defn- build-tree*
  "Implementation for build-tree. min-level is the floor level for this scope;
   any node with a level below min-level is out of scope and terminates processing."
  [lines min-level]
  (loop [idx 0
         result []]
    (if (>= idx (count lines))
      result
      (let [{:keys [level] :as node} (nth lines idx)]
        (if (< level min-level)
          ;; Level dropped below our scope, return what we have
          result
          (let [child-level (inc level)
                ;; Collect children: consecutive lines starting at a deeper level
                child-start (inc idx)
                child-end (loop [i child-start]
                            (if (and (< i (count lines))
                                     (>= (:level (nth lines i)) child-level))
                              (recur (inc i))
                              i))
                children (when (< child-start child-end)
                           (build-tree* (subvec lines child-start child-end) child-level))
                node' (assoc node :children (vec (or children [])))]
            (recur child-end
                   (conj result node'))))))))

(defn build-tree
  "Builds a nested tree from a flat sequence of parsed trace lines.
   Each node in the tree has :level, :type, :predicate, :source, and :children.
   Children are lines that follow at a higher nesting level until the level
   drops back to the same or lower level.
   
   Returns a sequence of root-level nodes."
  [trace-lines]
  (let [lines (vec trace-lines)]
    (if (empty? lines)
      []
      (let [min-level (transduce (map :level) min Long/MAX_VALUE lines)]
        (build-tree* lines min-level)))))

(defn- node-matches?
  "Returns true if a trace node's predicate or source matches the search term
   (case-insensitive)."
  [node search-term]
  (let [term (string/lower-case search-term)]
    (or (string/includes? (string/lower-case (:predicate node)) term)
        (string/includes? (string/lower-case (:source node)) term))))

(defn- mark-matches
  "Recursively marks nodes with :match? and :has-match? flags.
   :match? is true if this node directly matches the search.
   :has-match? is true if this node or any descendant matches."
  [node search-term]
  (let [children' (mapv #(mark-matches % search-term) (:children node))
        direct-match? (node-matches? node search-term)
        child-match? (some :has-match? children')]
    (assoc node
           :children children'
           :match? direct-match?
           :has-match? (boolean (or direct-match? child-match?)))))

(defn search-tree
  "Marks all nodes in the tree with match information for the given search term.
   Returns the tree with :match? and :has-match? flags on each node."
  [tree search-term]
  (if (string/blank? search-term)
    tree
    (mapv #(mark-matches % search-term) tree)))

(defn count-nodes
  "Counts the total number of nodes in the tree."
  [tree]
  (reduce (fn [acc node]
            (+ acc 1 (count-nodes (:children node))))
          0
          tree))
