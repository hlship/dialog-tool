(ns dialog-tool.commands
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clj-commons.ansi :refer [pout perr]]
            [clojure.string :as string]
            [dialog-tool.env :as env]
            [dialog-tool.template :as template]
            [dialog-tool.bundle :as bundle]
            [net.lewisship.cli-tools :as cli :refer [defcommand]]
            [dialog-tool.build :as build]
            [dialog-tool.project-file :as pf]))

(def debug-opt ["-d" "--debug" "Include debug sources"])

(defcommand debug
  "Run the project in the Dialog debugger."
  [width ["-w" "--width NUMBER" "Output width (omit to use terminal width)"
          :parse-fn parse-long
          :validate [some? "Not a number"
                     pos-int? "Must be at least one"]]]
  (let [project (pf/read-project)
        extra-args (cond-> []
                     width (conj "--width" width))
        cmd (-> [(pf/command-path project "dgdebug") "--quit"]
                (into extra-args)
                (into (pf/expand-sources project {:debug? true})))
        *process (p/process {:cmd cmd
                             :inherit true})
        {:keys [exit]} @*process]
    (cli/exit exit)))

(defcommand new-project
  "Create a new empty Dialog project from a template.

  The name of the project will match the directory name, if the project name is omitted."
  [:command "new"
   :args
   project-dir ["DIR" "New directory to create"
                :parse-fn fs/path
                :validate [#(not (fs/exists? %)) "Directory already exists"]]
   project-name ["NAME" "Name of project "
                 :optional true]]
  (template/create-from-template project-dir
                                 {:project-name (or project-name project-dir)}))

(defcommand build
  "Compile the project to a file ready to execute with an interpreter."
  [format (cli/select-option "-f" "--format FORMAT"
                             "Output format:"
                             #{:zblorb :z5 :z8 :aa})
   debug? debug-opt
   verbose ["-v" "--verbose" "Enable additional compiler output"]]
  (build/build-project (pf/read-project)
                       {:debug? debug?
                        :verbose? verbose
                        :format format}))

(defcommand bundle
  "Bundle the project into a Zip archive that can be deployed to a web host."
  []
  (bundle/bundle-project (pf/read-project)))

(defcommand run-project
  "Runs the project using the frotz command.

  Note: --dumb has output format issues.

  Use -- before any frotz arguments.
  "
  [debug? debug-opt
   dumb? ["-D" "--dumb" "Run using dfrotz instead of frotz"]
   :args
   frotz-args ["ARGS" "Extra arguments passed to frotz"
               :optional true
               :repeatable true]
   :in-order true
   :command "run"]
  (let [project (pf/read-project)
        {:keys [format]} project
        format' (if (= format :aa)
                  (do
                    (perr [:faint "Project format is aa; compiling to z8 for frotz"])
                    :z8)
                  format)
        path    (build/build-project project
                                     {:format format'
                                      :debug? debug?})
        command (concat [(if dumb? "dfrotz" "frotz")]
                        (when dumb?
                          ["-m" "-q"])
                        frotz-args
                        [(str path)])]
    (env/debug-command command)
    (apply p/shell command)))

(defcommand sources
  "Print the sources for the project in compilation order."
  [debug? debug-opt
   one? ["-1" "--single-line" "Output as a single line, colon-separated"]]
  (let [project (pf/read-project)
        paths (pf/expand-sources project {:debug? debug?})]
    (if one?
      (println (string/join ":" paths))
      (let [longest (apply max (map count paths))]
        (doseq [path paths]
          (pout [{:font :cyan
                  :width longest} path]))))))
