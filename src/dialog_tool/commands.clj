(ns dialog-tool.commands
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clj-commons.ansi :refer [pcompose]]
            [net.lewisship.cli-tools :as cli :refer [defcommand]]
            [clojure.java.browse :as browse]
            [dialog-tool.skein.service :as service]
            [dialog-tool.project-file :as pf]))

(def path-opt ["-p" "--path PATH" "Path to root directory of Dialog project."
               :default "."])

(defcommand debug
  "Run the project in the Dialog debugger."
  [path path-opt
   width ["-w" "--width NUMBER" "Output width (omit to use terminal width)"
          :parse-fn parse-long
          :validate [some? "Not a number"
                     pos-int? "Must be at least one"]]]
  (let [project (pf/read-project path)
        extra-args (cond-> []
                           width (conj "--width" width))
        cmd (-> ["dgdebug" "--quit"]
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
  [path path-opt
   seed [nil "--seed NUMBER" "Random number generator seed to use, if creating a new Skein"
         :parse-fn parse-long
         :validate [some? "Not a number"
                    pos-int? "Must be at least one"]]
   skein ["-f" "--file SKEIN" "Path to file containing the Skein; will be created if necessary."
          :default "game.skein"]]
  (let [project (pf/read-project path)
        {:keys [port]} (service/start! project skein (cond-> nil
                                                             seed (assoc :seed seed)))
        url (str "http://localhost:" port "/index.html")]
    (pcompose [:bold (if (fs/exists? skein) "Loading" "Creating")
               " " skein " ..."])
    (pcompose [:faint "Skein service started on port " port " ..."])
    (pcompose "Hit " [:bold "Ctrl+C"] " when done")
    (browse/browse-url url))
  ;; Hang forever
  @(promise))

(defcommand compile-project
  "Compiles the project to a file ready execute with an interpreter."
  [:command "compile"])

(defcommand bundle
  "Bundles a project into a Zip archive that can be deployed to a web host."
  [])

