(ns dialog-tool.skein.trace
  "Parses Dialog debugger trace output into a flat node map.
   
   Trace output lines have the format:
     |N TYPE predicate source:line
   
   Where:
   - | identifies the line as trace output
   - N is the numeric nesting level
   - TYPE is ENTER, QUERY, FOUND, or NOW
   - predicate is the Dialog predicate being traced
   - source:line is the source file and line number

   The tree is stored as a flat map of `{id -> node}`. Each node has:
   - :id        - unique numeric identifier
   - :children  - vector of child node IDs
   - :type      - :enter, :query, :found, or :now
   - :predicate - the predicate text
   - :source    - source file:line reference
   - :expanded  - boolean, whether the node is expanded in the UI

   Node 0 is an invisible root whose :children are the top-level nodes."
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
  "Implementation for build-tree. Processes lines at the given min-level scope,
   adding nodes to the *nodes volatile map and returning a vector of IDs
   for the nodes at this level."
  [lines min-level *nodes *next-id]
  (loop [idx 0
         result []]
    (if (>= idx (count lines))
      result
      (let [{:keys [level] :as line-data} (nth lines idx)]
        (if (< level min-level)
          result
          (let [node-id (vswap! *next-id inc)
                child-level (inc level)
                child-start (inc idx)
                child-end (loop [i child-start]
                            (if (and (< i (count lines))
                                     (>= (:level (nth lines i)) child-level))
                              (recur (inc i))
                              i))
                child-ids (when (< child-start child-end)
                            (build-tree* (subvec lines child-start child-end)
                                         child-level *nodes *next-id))
                node (assoc line-data
                            :id node-id
                            :children (vec (or child-ids [])))]
            (vswap! *nodes assoc node-id node)
            (recur child-end
                   (conj result node-id))))))))

(defn build-tree
  "Builds a flat node map from a sequence of parsed trace lines.
   Returns a map of `{id -> node}` with an invisible root node at id 0.
   The root's :children contains the IDs of the top-level nodes."
  [trace-lines]
  (let [lines (vec trace-lines)]
    (if (empty? lines)
      {0 {:id 0 :children []}}
      (let [min-level (transduce (map :level) min Long/MAX_VALUE lines)
            *nodes (volatile! {})
            *next-id (volatile! 0)
            root-ids (build-tree* lines min-level *nodes *next-id)]
        (assoc @*nodes 0 {:id 0 :children root-ids})))))

;; ---------------------------------------------------------------------------
;; Node lookup and tree stats
;; ---------------------------------------------------------------------------

(defn get-node
  "Retrieves a node by its :id from the nodes map."
  [nodes id]
  (get nodes id))

(defn count-nodes
  "Counts the number of visible nodes (excludes the invisible root)."
  [nodes]
  (dec (count nodes)))

;; ---------------------------------------------------------------------------
;; Expand/collapse operations
;; ---------------------------------------------------------------------------

(defn expand-all
  "Sets :expanded true on all nodes that have children."
  [nodes]
  (update-vals nodes
               (fn [node]
                 (assoc node :expanded (boolean (seq (:children node)))))))

(defn collapse-all
  "Sets :expanded false on all nodes."
  [nodes]
  (update-vals nodes #(assoc % :expanded false)))

(defn toggle-expanded
  "Toggles the :expanded flag on the node with the given :id."
  [nodes id]
  (update-in nodes [id :expanded] not))

;; ---------------------------------------------------------------------------
;; Search
;; ---------------------------------------------------------------------------

(defn- node-matches?
  "Returns true if a trace node's predicate or source matches the search term
   (case-insensitive)."
  [node search-term]
  (let [term (string/lower-case search-term)]
    (or (string/includes? (string/lower-case (or (:predicate node) "")) term)
        (string/includes? (string/lower-case (or (:source node) "")) term))))

(defn search-tree
  "Marks all nodes with :match? and :has-match? flags for the given search term.
   :match? is true if the node directly matches.
   :has-match? is true if the node or any descendant matches.
   Returns the updated nodes map."
  [nodes search-term]
  (if (string/blank? search-term)
    nodes
    (let [;; First pass: mark direct matches
          with-matches (update-vals nodes
                                    (fn [node]
                                      (assoc node :match? (node-matches? node search-term))))
          ;; Second pass: propagate :has-match? bottom-up via post-order traversal
          has-match-cache (volatile! {})]
      (letfn [(compute-has-match [id]
                (if-let [cached (get @has-match-cache id)]
                  cached
                  (let [node (get with-matches id)
                        result (boolean
                                (or (:match? node)
                                    (some compute-has-match (:children node))))]
                    (vswap! has-match-cache assoc id result)
                    result)))]
        ;; Compute for all nodes
        (doseq [id (keys with-matches)]
          (compute-has-match id))
        ;; Apply :has-match? flags
        (update-vals with-matches
                     (fn [node]
                       (assoc node :has-match? (get @has-match-cache (:id node)))))))))

(defn expand-to-matches
  "Sets :expanded true on nodes with :has-match? that have children,
   and :expanded false on others. The nodes map must have been processed
   by search-tree first."
  [nodes]
  (update-vals nodes
               (fn [node]
                 (assoc node :expanded
                        (boolean (and (:has-match? node)
                                      (seq (:children node))))))))

(defn find-first-match
  "Walks the tree depth-first from the root and returns the :id of the first
   node with :match? true, or nil if no match is found."
  [nodes]
  (letfn [(dfs [ids]
            (when (seq ids)
              (let [id (first ids)
                    node (get nodes id)]
                (if (:match? node)
                  id
                  (or (dfs (:children node))
                      (dfs (rest ids)))))))]
    (dfs (:children (get nodes 0)))))

(defn parse-source
  "Parses a source string like 'file.dg:42' into [file-path line-number],
   or returns nil if it can't be parsed."
  [source]
  (when source
    (let [idx (string/last-index-of source ":")]
      (when (and idx (pos? idx))
        (let [file-path (subs source 0 idx)
              line-str (subs source (inc idx))]
          (when-let [line (parse-long line-str)]
            [file-path line]))))))
