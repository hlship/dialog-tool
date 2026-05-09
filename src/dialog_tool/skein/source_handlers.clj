(ns dialog-tool.skein.source-handlers
  "Ring handlers for source viewing endpoints.
  These serve standalone HTML pages and fragments, not part of the hyper
  reactive UI. Used by the trace modal's source links and previews."
  (:require [babashka.fs :as fs]
            [clojure.string :as string]
            [dialog-tool.project-file :as pf]
            [dialog-tool.skein.syntax :as syntax]
            [dialog-tool.skein.trace :as trace]
            [selmer.parser :as s]))

(defn- get-session
  "Reads the current session from hyper's app-state."
  []
  (get-in @(deref (requiring-resolve 'dialog-tool.skein.service/*handler-app-state))
          [:global :session]))

(defn- resolve-trace-source
  "Given the session and a node ID string, resolves the trace node's source
   to a [file-path line resolved-path] triple, or nil."
  [session node-id-str]
  (let [nodes (get-in session [:trace :nodes])
        node-id (parse-long node-id-str)
        node (when (and nodes node-id) (trace/get-node nodes node-id))
        [file-path line] (when node (trace/parse-source (:source node)))]
    (when file-path
      (let [project (-> session :process :project)
            root-dir (pf/root-dir project)
            resolved (fs/path root-dir file-path)]
        (when (fs/exists? resolved)
          [file-path line resolved])))))

(defn- read-source-lines
  [resolved-path]
  (let [content (slurp (str resolved-path))
        raw-lines (string/split content #"\n" -1)]
    (if (and (> (count raw-lines) 1)
             (= "" (peek raw-lines)))
      (pop (vec raw-lines))
      (vec raw-lines))))

(defn view-source
  "Serves a standalone HTML page displaying a Dialog source file."
  [req]
  (let [node-id (-> req :path-params :id)]
    (if-let [[file-path line resolved] (resolve-trace-source (get-session) node-id)]
      (let [raw-lines (read-source-lines resolved)
            dg? (string/ends-with? file-path ".dg")
            lines (map-indexed
                   (fn [idx text]
                     (let [n (inc idx)]
                       {:number n
                        :text (if dg?
                                (syntax/highlight-line text)
                                (syntax/html-escape text))
                        :highlighted (= n line)}))
                   raw-lines)]
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (s/render-file "skein/source.html"
                              {:file-path file-path
                               :line line
                               :line-count (count raw-lines)
                               :lines lines})})
      {:status 404
       :headers {"Content-Type" "text/plain"}
       :body "Source not found"})))

(defn source-preview
  "Returns an HTML fragment for hover preview of source code."
  [req]
  (let [node-id (-> req :path-params :id)]
    (if-let [[file-path line resolved] (resolve-trace-source (get-session) node-id)]
      (let [raw-lines (read-source-lines resolved)
            dg? (string/ends-with? file-path ".dg")
            context 4
            start (max 0 (- line context 1))
            end (min (count raw-lines) (+ line context))
            window (subvec raw-lines start end)
            lines (map-indexed
                   (fn [idx text]
                     (let [n (+ start idx 1)]
                       {:number n
                        :text (if dg?
                                (syntax/highlight-line text)
                                (syntax/html-escape text))
                        :highlighted (= n line)}))
                   window)]
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (s/render-file "skein/source-preview.html"
                              {:file-path file-path
                               :line line
                               :lines lines})})
      {:status 404
       :headers {"Content-Type" "text/plain"}
       :body "Source not found"})))
