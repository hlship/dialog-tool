(ns dialog-tool.build
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clj-commons.ansi :refer [perr]]
            [clojure.string :as str]
            [dialog-tool.project-file :as pf]
            [net.lewisship.cli-tools :as cli]))

(defn- dialogc-args
  [format options project output-path]
  (let [{:keys [verbose? debug?]} options
        {:keys [build]} project
        build' (merge (:default build)
                            (get build format))
        build-options (:options build')]
    (cond-> ["--format" (name format)
             "--output" (str output-path)]

      verbose? (conj "--verbose")

      (not debug?) (conj "--strip")

      (seq build-options) (into build-options))))

(defn- invoke-dialogc
  "Invokes dialogc to compile the sources from the project.

  Returns the path to the compiled file."
  [format project sources output-dir options]
  (let [ext         (if (= format :aa)
                      "aastory"
                      (name format))
        output-path (fs/path output-dir (str (:name project) "." ext))
        args        (into (dialogc-args format options project output-path)
                          sources)
        command     (into ["dialogc"] args)
        _           (perr [:cyan "Building " output-path " ..."])
        _           (when (:verbose? options)
                      (perr [:cyan (str/join " " command)]))
        {:keys [exit]} @(p/process {:cmd     command
                                    :inherit true})]
    (when-not (zero? exit)
      (cli/exit exit))
    output-path))

(defn build-project
  "Builds a project; returns the path of the compiled file."
  [project options]
  (let [{:keys [format debug?]} options
        format     (or format (:format project))
        _     (when-not format
                (cli/abort "No :format defined for project"))
        output-dir (fs/path "out" (if debug? "debug" "release"))
        sources    (pf/expand-sources project options)]
    (fs/create-dirs output-dir)
    (invoke-dialogc format project sources output-dir options)))
