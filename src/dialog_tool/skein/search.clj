(ns dialog-tool.skein.search
  "In-memory full-text search over blessed knot content using Apache Lucene.

  Each knot with a blessed :response is indexed as a document with fields for
  command, response (ANSI-stripped), and label. The index is lazily built on first
  search and transparently rebuilt when the tree changes (including undo/redo)."
  (:require [clojure.string :as string]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.ui.ansi :as ansi])
  (:import (org.apache.lucene.analysis.standard StandardAnalyzer)
           (org.apache.lucene.document Document Field$Store StringField TextField)
           (org.apache.lucene.index DirectoryReader IndexWriter IndexWriterConfig)
           (org.apache.lucene.queryparser.classic MultiFieldQueryParser QueryParser)
           (org.apache.lucene.search IndexSearcher)
           (org.apache.lucene.store ByteBuffersDirectory)))

;; The index is a map of {:searcher :analyzer :directory :reader :source-tree}.
;; :source-tree is the tree object the index was built from; an `identical?` check
;; detects when the tree has changed (including via undo/redo which swap entire snapshots).
(defonce ^:private *index (atom nil))

(def ^:private search-fields
  "Fields searched by the multi-field query parser."
  (into-array String ["command" "response" "label"]))

(defn- create-document
  "Creates a Lucene Document for a single knot."
  ^Document [knot]
  (let [doc (Document.)]
    (.add doc (StringField. "id" (str (:id knot)) Field$Store/YES))
    (.add doc (TextField. "command" (or (:command knot) "") Field$Store/YES))
    (.add doc (TextField. "response" (ansi/strip-ansi (or (:response knot) "")) Field$Store/YES))
    (.add doc (TextField. "label" (or (:label knot) "") Field$Store/YES))
    doc))

(defn- build-index!
  "Builds a new in-memory Lucene index from all blessed knots, installs it
  as the current index, and closes the previous one.  Typically completes in
  under 10 ms for a few hundred knots."
  [tree]
  (let [old-index @*index
        analyzer (StandardAnalyzer.)
        directory (ByteBuffersDirectory.)
        writer (IndexWriter. directory (IndexWriterConfig. analyzer))]
    (doseq [knot (tree/all-knots tree)
            :when (:response knot)]
      (.addDocument writer (create-document knot)))
    (.close writer)
    (let [reader (DirectoryReader/open directory)
          new-index {:searcher (IndexSearcher. reader)
                     :analyzer analyzer
                     :directory directory
                     :reader reader
                     :source-tree tree}]
      (reset! *index new-index)
      (when old-index
        (.close ^DirectoryReader (:reader old-index))
        (.close ^ByteBuffersDirectory (:directory old-index)))
      new-index)))

(defn- ensure-index
  "Returns the current search index, rebuilding if the tree has changed.
  Uses `identical?` on the tree object — since undo/redo swaps entire immutable
  snapshots, any mutation is detected."
  [tree]
  (let [index @*index]
    (if (and index (identical? (:source-tree index) tree))
      index
      (build-index! tree))))

(defn- extract-snippet
  "Extracts a text snippet around the first occurrence of any query term
  in the response text.  Returns at most `max-len` characters centered
  on the match, with ellipsis when truncated."
  [^String text query-str max-len]
  (if (string/blank? text)
    ""
    (let [terms (string/split (string/lower-case query-str) #"\s+")
          lower-text (string/lower-case text)
          pos (reduce (fn [best term]
                        (let [i (string/index-of lower-text term)]
                          (if (and i (or (nil? best) (< ^long i ^long best)))
                            i
                            best)))
                      nil
                      terms)]
      (if (nil? pos)
        (subs text 0 (min (count text) max-len))
        (let [half (quot max-len 2)
              start (max 0 (- ^long pos half))
              end (min (count text) (+ start max-len))
              start (max 0 (- end max-len))]
          (str (when (pos? start) "…")
               (subs text start end)
               (when (< end (count text)) "…")))))))

(defn highlight-snippet
  "Returns hiccup markup for the snippet with matching query terms
  wrapped in [:mark] elements.  Non-matching text is left as plain strings."
  [snippet query-str]
  (if (or (string/blank? snippet) (string/blank? query-str))
    snippet
    (let [terms (string/split (string/lower-case query-str) #"\s+")
          pattern (re-pattern
                   (str "(?i)("
                        (string/join "|" (map #(java.util.regex.Pattern/quote %) terms))
                        ")"))
          matcher (re-matcher pattern snippet)]
      (loop [pos 0
             result []]
        (if (.find matcher)
          (let [match-start (.start matcher)
                match-end (.end matcher)
                before (when (< pos match-start)
                         (subs snippet pos match-start))
                matched (subs snippet match-start match-end)]
            (recur match-end
                   (cond-> result
                     before (conj before)
                     true (conj [:mark.bg-warning.text-warning-content.rounded.px-0.5 matched]))))
          (let [trailing (when (< pos (count snippet))
                           (subs snippet pos))]
            (cond-> result
              trailing (conj trailing))))))))

(defn search-knots
  "Searches for knots matching `query-str`.  Returns a vector of result maps
  with keys :knot-id, :command, :snippet, :label, and :score.

  Returns nil when the query is blank.  The index is lazily rebuilt when
  the tree has changed since the last search (checked via `identical?`)."
  [tree query-str max-results]
  (when-not (string/blank? query-str)
    (try
      (let [{:keys [^IndexSearcher searcher analyzer]} (ensure-index tree)
            ;; Lowercase because StandardAnalyzer lowercases indexed terms,
            ;; and Lucene doesn't analyze wildcard/prefix terms.
            escaped (QueryParser/escape (string/lower-case query-str))
            ;; When the query doesn't end with a space, the last term is
            ;; still being typed — add a wildcard for prefix matching
            ;; (e.g. "emergency blan" → "emergency blan*" matches "blanket").
            query-str' (if (or (string/ends-with? query-str " ")
                               (string/blank? escaped))
                         escaped
                         (let [terms (string/split escaped #"\s+")]
                           (string/join " " (concat (butlast terms)
                                                    [(str (last terms) "*")]))))
            parser (doto (MultiFieldQueryParser. search-fields analyzer)
                     (.setDefaultOperator QueryParser/AND_OPERATOR))
            query (.parse parser query-str')
            hits (.-scoreDocs (.search searcher query (int max-results)))]
        (mapv (fn [score-doc]
                (let [doc (.doc searcher (.-doc score-doc))
                      response (.get doc "response")]
                  {:knot-id (parse-long (.get doc "id"))
                   :command (.get doc "command")
                   :snippet (extract-snippet response query-str 400)
                   :label (.get doc "label")
                   :score (.-score score-doc)}))
              hits))
      (catch Exception _
        ;; Malformed query or other Lucene error; treat as no results
        nil))))

(defn close!
  "Closes and releases the current search index resources."
  []
  (when-let [index @*index]
    (reset! *index nil)
    (.close ^DirectoryReader (:reader index))
    (.close ^ByteBuffersDirectory (:directory index))))
