(ns dialog-tool.build
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clj-commons.ansi :refer [perr]]
            [clojure.string :as str]
            [dialog-tool.project-file :as pf]
            [net.lewisship.cli-tools :as cli]))

(defn- dialogc-args
  [format options project output-path]
  (let [{:keys [verbose? test?]} options
        {:keys [build]} project
        build' (merge (:default build)
                            (get build format))
        build-options (:options build')
        zblorb? (= :zblorb format)]
    (cond-> ["--format" (name format)
             "--output" (str output-path)]

      verbose? (conj "--verbose")

      (not test?) (conj "--strip")

      (and zblorb? (fs/exists? "cover.png")) (conj "--cover" "cover.png")

      ;; Resources are images and sounds that can be referenced in project source
      ;; https://linusakesson.net/dialog/docs/io.html#resources

      (fs/exists? "resources") (conj "--resources" "resources")

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
        _           (perr [:cyan "Creating " output-path " ..."])
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
  (let [ {:keys [format test?]} options
        format     (or format (:format project))
        output-dir (fs/path "out" (name format) (if test? "test" "release"))
        sources    (pf/expand-sources project {:debug? test?})]
    (when (fs/exists? output-dir)
      (fs/delete-tree output-dir))
    (fs/create-dirs output-dir)
    (invoke-dialogc format project sources output-dir options)))
