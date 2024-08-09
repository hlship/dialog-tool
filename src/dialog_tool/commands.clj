(ns dialog-tool.commands
  (:require [babashka.process :as p]
            [net.lewisship.cli-tools :as cli :refer [defcommand]]
            [dialog-tool.skein.process :as sp]
            [dialog-tool.skein.file :as sk.file]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.project-file :as pf]))

(def path-opt ["-p" "--path PATH" "Path to root directory of Dialog project."
               :default "."])

(defcommand debug
  "Run the project in the Dialog debugger."
  [path path-opt
   width ["-w" "--width NUMBER" "Output width (omit to use terminal width)"
          :parse-fn parse-long
          :validate [some? "Not a number"
                     pos-int? "Must be greater than zero."]]]
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
  [])

(defcommand compile-project
  "Compiles the project to a file ready execute with an interpreter."
  [:command "compile"])

(defcommand bundle
  "Bundles a project into a Zip archive that can be deployed to a web host."
  [])

(comment
  (-> (pf/read-project "../sanddancer-dialog") (pf/expand-sources {:debug? true}))

  (def proc (sp/start-debug-process! "dgdebug" (pf/read-project "../sanddancer-dialog")))

  (sp/read-response! proc)

  (-> proc :process .isAlive)

  (time (sp/send-command! proc "brood stories"))
  (sp/send-command! proc "look")
  (sp/kill! proc)


  (let [id1 (tree/next-id)
        id2 (tree/next-id)]
    (-> (tree/new-tree)
        (assoc-in [:meta :seed] 998877)
        (tree/update-response 0 "Wicked Cool Adventure\n")
        (tree/add-child 0 id1 "look" "room description")
        (tree/add-child id1 id2 "get lamp" "You pick up the lamp.\n")
        (tree/bless-response id2)
        (tree/update-response id2 "You pick up the dusty lamp.\n")
        (sk.file/write-skein *out*)))


  )

