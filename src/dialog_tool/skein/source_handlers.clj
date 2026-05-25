(ns dialog-tool.skein.source-handlers
  "Ring handlers for source viewing endpoints.
  These serve standalone HTML pages and fragments, not part of the hyper
  reactive UI. Used by the trace modal's source links and previews."
  (:require [babashka.fs :as fs]
            [clojure.string :as string]
            [dev.onionpancakes.chassis.core :as chassis]
            [dialog-tool.project-file :as pf]
            [dialog-tool.skein.syntax :as syntax]
            [dialog-tool.skein.trace :as trace]
            [dialog-tool.skein.ui.ansi :as ansi]
            [selmer.parser :as s]))

(defn parse-error-location
  "Given a raw dgdebug error string (possibly containing ANSI codes),
   returns a map {:file-path \"...\" :line N} if the error message
   contains a file/line reference, or nil otherwise.

   Matches lines of the form:
     Error: <filepath>, line <N>: <message>"
  [error]
  (when error
    (let [plain (ansi/strip-ansi error)]
      (when-let [[_ file-path line-str] (re-find #"Error:\s+(.+?),\s+line\s+(\d+):" plain)]
        {:file-path (string/trim file-path)
         :line      (parse-long line-str)}))))

(defn- request->globals
  "Reads the current session from hyper's app-state on the request."
  [req]
  (when-let [*app-state (:hyper/app-state req)]
    (get @*app-state :global)))

(defn- resolve-trace-source
  "Given the session and a node ID string, resolves the trace node's source
   to a [file-path line resolved-path] triple, or nil."
  [globals node-id-str]
  (let [{:keys [nodes]} (:modal globals)
        node-id (parse-long node-id-str)
        node    (when (and nodes node-id)
                  (trace/get-node nodes node-id))
        [file-path line] (when node (trace/parse-source (:source node)))]
    (when file-path
      (let [project  (get-in globals [:session :process :project])
            root-dir (pf/root-dir project)
            resolved (fs/path root-dir file-path)]
        (when (fs/exists? resolved)
          [file-path line resolved])))))

(defn- read-source-lines
  [resolved-path]
  (let [content   (slurp (str resolved-path))
        raw-lines (string/split content #"\n" -1)]
    (if (and (> (count raw-lines) 1)
             (= "" (peek raw-lines)))
      (pop (vec raw-lines))
      (vec raw-lines))))

(defn render-source-snippet
  "Renders a source snippet as hiccup, centered on the given line.
   file-path is as it appears in the dgdebug error message — relative to the
   JVM working directory. Returns nil if the file cannot be resolved."
  [file-path line]
  (let [resolved (fs/absolutize (fs/path file-path))]
    (when (fs/exists? resolved)
      (let [raw-lines (read-source-lines resolved)
            dg?       (string/ends-with? file-path ".dg")
            context   4
            start     (max 0 (- line context 1))
            end       (min (count raw-lines) (+ line context))
            window    (subvec raw-lines start end)]
        [:div.border.rounded.overflow-hidden.text-xs.font-mono
         [:div.px-2.py-1.bg-gray-50.border-b.border-gray-200.text-gray-500.truncate
          (str file-path ":" line)]
         [:table.border-collapse.w-full.leading-relaxed
          [:tbody
           (map-indexed
             (fn [idx text]
               (let [n            (+ start idx 1)
                     highlighted? (= n line)]
                 [:tr {:class (when highlighted? "bg-yellow-200")}
                  [:td.text-right.pr-2.pl-2.select-none.border-r.border-gray-200.whitespace-nowrap
                   {:class (if highlighted? "text-amber-800 font-bold" "text-gray-400")}
                   n]
                  [:td.px-2.whitespace-pre
                   (chassis/raw (if dg?
                                  (syntax/highlight-line text)
                                  (syntax/html-escape text)))]]))
             window)]]]))))

(defn view-source
  "Serves a standalone HTML page displaying a Dialog source file."
  [req]
  (let [node-id (-> req :path-params :id)
        globals  (request->globals req)]
    (if-let [[file-path line resolved] (resolve-trace-source globals node-id)]
      (let [raw-lines (read-source-lines resolved)
            dg?       (string/ends-with? file-path ".dg")
            lines     (map-indexed
                        (fn [idx text]
                          (let [n (inc idx)]
                            {:number      n
                             :text        (if dg?
                                            (syntax/highlight-line text)
                                            (syntax/html-escape text))
                             :highlighted (= n line)}))
                        raw-lines)]
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body    (s/render-file "skein/source.html"
                                 {:file-path  file-path
                                  :line       line
                                  :line-count (count raw-lines)
                                  :lines      lines})})
      {:status  404
       :headers {"Content-Type" "text/plain"}
       :body    "Source not found"})))

(defn source-preview
  "Returns an HTML fragment for hover preview of source code."
  [req]
  (let [node-id (-> req :path-params :id)
        globals  (request->globals req)]
    (if-let [[file-path line resolved] (resolve-trace-source globals node-id)]
      (let [raw-lines (read-source-lines resolved)
            dg?       (string/ends-with? file-path ".dg")
            context   4
            start     (max 0 (- line context 1))
            end       (min (count raw-lines) (+ line context))
            window    (subvec raw-lines start end)
            lines     (map-indexed
                        (fn [idx text]
                          (let [n (+ start idx 1)]
                            {:number      n
                             :text        (if dg?
                                            (syntax/highlight-line text)
                                            (syntax/html-escape text))
                             :highlighted (= n line)}))
                        window)]
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body    (s/render-file "skein/source-preview.html"
                                 {:file-path file-path
                                  :line      line
                                  :lines     lines})})
      {:status  404
       :headers {"Content-Type" "text/plain"}
       :body    "Source not found"})))
