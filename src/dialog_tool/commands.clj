(ns dialog-tool.commands
  (:require [babashka.process :as p]
            [net.lewisship.cli-tools :as cli :refer [defcommand]]
            [dialog-tool.project-file :as pf]))

(def path-opt ["-p" "--path PATH" "Path to root directory of Dialog project."
               :default "."])
(def debugger-opt ["-D" "--debugger PATH" "Path to Dialog dgdebug executable."
                   :default "/usr/local/bin/dgdebug"])

(defcommand debug
  "Run the project in the Dialog debugger."
  [path path-opt
   debugger debugger-opt
   width ["-w" "--width NUMBER" "Output width (omit to use terminal width)"
          :parse-fn parse-long
          :validate [some? "Not a number"
                     pos-int? "Must be greater than zero."]]]
  (let [project (pf/read-project path)
        extra-args (cond-> []
                           width (conj "--width" width))
        cmd (-> [debugger "--quit" "-D"]
                (into extra-args)
                (into (pf/expand-sources project {:debug? true})))
        *process (p/process {:cmd     cmd
                             :inherit true})
        {:keys [exit]} @*process]
    (cli/exit exit)))

(defcommand new-project
  "Creates a new empty Dialog project from a template."
  [:command "new"])

(defcommand skein
  "Runs the Skein UI to test the Dialog project."
  [])

(defcommand compile
  "Compiles the project to a file ready execute with an interpreter."
  [])



(defcommand bundle
  "Bundles a project into a Zip archive that can be deployed to a web host."
  [])
