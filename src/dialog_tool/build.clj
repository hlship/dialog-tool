(ns dialog-tool.build
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clj-commons.ansi :refer [perr]]
            [clojure.string :as str]
            [dialog-tool.project-file :as pf]
            [net.lewisship.cli-tools :as cli]))

(defn- dialogc-args
  [format options output-path]
  (let [{:keys [verbose? test?]} options]
    (cond-> ["--format" (name format)
             "--output" (str output-path)]

      verbose? (conj "--verbose")

      (not test?) (conj "--strip"))))

(defn- invoke-dialogc
  [format project sources output-dir options]
  (let [ext         (if (= format :aa)
                      "aastory"
                      (name format))
        output-path (fs/path output-dir (str (:name project) "." ext))
        args        (into (dialogc-args format options output-path)
                          sources)
        command     (into ["dialogc"] args)
        _           (perr [:cyan "Creating " output-path " ..."])
        _           (when (:verbose? options)
                      (perr [:cyan (str/join " " command)]))
        {:keys [exit]} @(p/process {:cmd     command
                                    :inherit true})]
    (when-not (zero? exit)
      (cli/exit exit))))

(defn build-project
  [project options]
  (let [{:keys [format test?]} options
        output-dir (fs/path "out" (name format) (if test "test" "release"))
        sources    (pf/expand-sources project {:debug? test?})]
    (when (fs/exists? output-dir)
      (fs/delete-tree output-dir))
    (fs/create-dirs output-dir)
    ;; TODO: :zblorb special setup
    (invoke-dialogc format project sources output-dir options)))
