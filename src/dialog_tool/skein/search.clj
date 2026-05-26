(ns dialog-tool.skein.search
  "In-memory full-text search over blessed knot content using Apache Lucene.

  Each knot with a blessed :response is indexed as a document with fields for
  command, response (ANSI-stripped), and label. The index is lazily built on first
  search and transparently rebuilt when the tree changes (including undo/redo).

  Uses EnglishAnalyzer for stemming (\"running\" matches \"run\") and Lucene's
  Highlighter for accurate snippet extraction and term markup."
  (:require [clojure.string :as string]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.ui.ansi :as ansi])
  (:import (org.apache.lucene.analysis.en EnglishAnalyzer)
           (org.apache.lucene.document Document Field$Store StringField TextField)
           (org.apache.lucene.index DirectoryReader IndexWriter IndexWriterConfig)
           (org.apache.lucene.queryparser.classic MultiFieldQueryParser QueryParser)
           (org.apache.lucene.search IndexSearcher)
           (org.apache.lucene.analysis.tokenattributes CharTermAttribute)
           (org.apache.lucene.search.highlight Formatter Highlighter QueryScorer SimpleSpanFragmenter)
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
        analyzer (EnglishAnalyzer.)
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

;; Sentinel characters used to delimit highlighted terms returned by the
;; Lucene Formatter. These are control characters that never appear in IF text.
(def ^:private mark-open "\u0001")
(def ^:private mark-close "\u0002")

(defn- marked->hiccup
  "Splits a Lucene-highlighted string (sentinel mark-open/mark-close around matched
  tokens) into a hiccup sequence of plain strings and [:mark …] elements."
  [^String s]
  (when s
    (loop [remaining s
           result    []]
      (let [open-idx (.indexOf remaining (int \u0001))]
        (if (neg? open-idx)
          (if (.isEmpty remaining) result (conj result remaining))
          (let [close-idx (.indexOf remaining (int \u0002))
                before    (subs remaining 0 open-idx)
                marked    (subs remaining (inc open-idx) close-idx)]
            (recur (subs remaining (inc close-idx))
                   (cond-> result
                     (pos? open-idx)
                     (conj before)
                     true
                     (conj [:strong marked])))))))))

(defn- stem-term
  "Returns the first token the analyzer produces for term, which is the stemmed
  form used in the index.  Falls back to term itself if the analyzer emits no
  tokens (e.g. a stop word)."
  [^EnglishAnalyzer analyzer ^String term]
  (with-open [ts (.tokenStream analyzer "response" term)]
    (let [attr (.getAttribute ts CharTermAttribute)]
      (.reset ts)
      (if (.incrementToken ts) (str attr) term))))

(defn- highlight-field
  "Returns a hiccup sequence for the best matching fragment in field-text,
  with matched terms wrapped in [:mark …]. Returns nil when field-text is blank
  or contains no match.

  Creates a fresh QueryScorer(query, reader, field-name) per call so that
  prefix/wildcard queries are expanded against the index for the correct field,
  and scorer state is never shared across documents or fields."
  [^org.apache.lucene.search.Query query ^DirectoryReader reader
   ^EnglishAnalyzer analyzer ^String field-name ^String field-text]
  (when-not (string/blank? field-text)
    (try
      (let [scorer      (QueryScorer. query reader field-name)
            hl-fmt      (reify Formatter
                          (highlightTerm [_ original token-group]
                            (if (pos? (.getTotalScore token-group))
                              (str mark-open original mark-close)
                              original)))
            highlighter (doto (Highlighter. hl-fmt scorer)
                          (.setTextFragmenter (SimpleSpanFragmenter. scorer 300)))]
        (some-> (.getBestFragment highlighter analyzer field-name field-text)
                (string/replace #"\n{2,}" "\n")
                string/triml
                marked->hiccup))
      (catch Exception _ nil))))

(defn- strip-command-echo
  "If the first line of response is an echo of command (with or without a
  leading \"> \"), drops it so the unified snippet does not repeat the
  command line that is always prepended separately."
  [^String response ^String command]
  (let [nl         (string/index-of response "\n")
        first-line (-> (if nl (subs response 0 nl) response)
                       (string/replace-first #"^>\s*" "")
                       string/trim
                       string/lower-case)]
    (if (= first-line (-> command string/trim string/lower-case))
      (if nl (subs response (inc nl)) "")
      response)))

(defn search-knots
  "Searches for knots matching `query-str`.  Returns a vector of result maps
  with keys :knot-id, :command, :snippet, :label, and :score.

  :snippet is a hiccup sequence of strings and [:mark …] elements, ready to
  render directly.  Matched terms are highlighted via Lucene's Highlighter
  (no post-hoc regex pass needed).

  Returns nil when the query is blank.  The index is lazily rebuilt when
  the tree has changed since the last search (checked via `identical?`)."
  [tree query-str max-results]
  (when-not (string/blank? query-str)
    (try
      (let [{:keys [^IndexSearcher searcher ^EnglishAnalyzer analyzer ^DirectoryReader reader]} (ensure-index tree)
            ;; Lowercase because EnglishAnalyzer lowercases indexed terms,
            ;; and Lucene doesn't analyze wildcard/prefix terms.
            escaped    (QueryParser/escape (string/lower-case query-str))
            ;; When the query doesn't end with a space, the last term is
            ;; still being typed — add a wildcard for prefix matching
            ;; (e.g. "emergency blan" → "emergency blan*" matches "blanket").
            query-str' (if (or (string/ends-with? query-str " ")
                               (string/blank? escaped))
                         escaped
                         (let [terms      (string/split escaped #"\s+")
                               last-stem  (stem-term analyzer (last terms))]
                           (string/join " " (concat (butlast terms)
                                                    [(str last-stem "*")]))))
            parser      (doto (MultiFieldQueryParser. search-fields analyzer)
                          (.setDefaultOperator QueryParser/AND_OPERATOR))
            query       (.parse parser query-str')
            hits        (.-scoreDocs (.search searcher query (int max-results)))]
        (mapv (fn [score-doc]
                (let [doc      (.doc searcher (.-doc score-doc))
                      command  (.get doc "command")
                      label    (.get doc "label")
                      response (.get doc "response")
                      response' (strip-command-echo response command)
                      cmd-hl   (or (highlight-field query reader analyzer "command" command)
                                   [command])
                      lbl-hl   (when-not (string/blank? label)
                                 (or (highlight-field query reader analyzer "label" label)
                                     [label]))
                      resp-hl  (or (highlight-field query reader analyzer "response" response')
                                   (when-not (string/blank? response')
                                     [(subs response' 0 (min 300 (count response')))]))
                      snippet  (-> ["> "]
                                   (into cmd-hl)
                                   (cond-> lbl-hl (into (cons "\n" lbl-hl)))
                                   (cond-> resp-hl (into (cons "\n" resp-hl))))]
                  {:knot-id (parse-long (.get doc "id"))
                   :command command
                   :label   label
                   :snippet snippet
                   :score   (.-score score-doc)}))
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
