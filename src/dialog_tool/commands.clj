(ns dialog-tool.commands
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clj-commons.ansi :as ansi]
            [clj-commons.ansi :refer [pout perr]]
            [clojure.string :as string]
            [dialog-tool.skein.file :as sk.file]
            [dialog-tool.skein.process :as sk.process]
            [dialog-tool.skein.session :as s]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.template :as template]
            [net.lewisship.cli-tools :as cli :refer [defcommand]]
            [clojure.java.browse :as browse]
            [dialog-tool.skein.service :as service]
            [dialog-tool.project-file :as pf]))

(defcommand debug
  "Run the project in the Dialog debugger."
  [width ["-w" "--width NUMBER" "Output width (omit to use terminal width)"
          :parse-fn parse-long
          :validate [some? "Not a number"
                     pos-int? "Must be at least one"]]]
  (let [project    (pf/read-project)
        extra-args (cond-> []
                     width (conj "--width" width))
        cmd        (-> ["dgdebug" "--quit"]
                       (into extra-args)
                       (into (pf/expand-sources project {:debug? true})))
        *process   (p/process {:cmd     cmd
                               :inherit true})
        {:keys [exit]} @*process]
    (cli/exit exit)))

(defcommand new-project
  "Creates a new empty Dialog project from a template."
  [:command "new"
   :as command
   :args
   project-dir ["DIR" "New directory to create"]]
  (when (fs/exists? project-dir)
    (cli/print-errors command "Directory already exists")
    (cli/exit -1))
  (template/create-from-template project-dir nil))

(defcommand skein
  "Runs the Skein UI to test the Dialog project."
  [seed [nil "--seed NUMBER" "Random number generator seed to use, if creating a new skein."
         :parse-fn parse-long
         :validate [some? "Not a number"
                    pos-int? "Must be at least one"]]
   skein ["-f" "--file SKEIN" "Path to file containing the Skein; will be created if necessary."
          :default "default.skein"]]
  (let [project (pf/read-project)
        {:keys [port]} (service/start! project skein (cond-> nil
                                                       seed (assoc :seed seed)))
        url     (str "http://localhost:" port "/index.html")]
    (pout [:bold (if (fs/exists? skein) "Loading" "Creating")
           " " skein " ..."])
    (pout [:faint "Skein service started on port " port " ..."])
    (pout "Hit " [:bold "Ctrl+C"] " when done")
    (browse/browse-url url))
  ;; Hang forever
  @(promise))

(defcommand compile-project
  "Compiles the project to a file ready execute with an interpreter."
  [:command "compile"])

(defcommand bundle
  "Bundles a project into a Zip archive that can be deployed to a web host."
  [])

(defn- trim-dot
  [path]
  (if (string/starts-with? path "./")
    (subs path 2)
    path))

(defn- compose-totals
  [totals]
  (ansi/compose [:green (:ok totals)] "/"
                [:yellow (:new totals)] "/"
                [:red (:error totals)]))

(defn- run-tests
  [project width skein-path]
  (let [tree      (sk.file/load-skein skein-path)
        process   (sk.process/start-debug-process! project (get-in tree [:meta :seed]))
        session   (-> (s/create-loaded! process skein-path tree)
                      (s/enable-undo false))
        leaf-ids  (->> tree
                       tree/leaf-knots
                       (map :id))
        test-leaf (fn [session id]
                    (print ".") (flush)
                    (s/replay-to! session id))
        path'     (trim-dot skein-path)
        spaces    (- width (count path'))
        session'  (do
                    (printf "Testing %s%s: "
                            (apply str (repeat spaces " "))
                            path')
                    (reduce test-leaf session leaf-ids))
        totals    (s/totals session')]
    (print " ")
    (println (compose-totals totals))
    totals))

(defcommand test-project
  "Uses the available skein(s) to test the project.

  Outputs the number of correct knots, the number of new knots
  (no prior response), and the number of error knots (conflicting response).

  Exits with 0 if all knots are correct, or with 1 if there are any errors."
  [:args
   skein-file ["SKEIN-FILE" "Path to single skein file to test"
               :optional true]
   :command "test"]
  (let [project     (pf/read-project)
        skein-paths (if skein-file
                      [skein-file]
                      (->> (fs/glob "." "*.skein")
                           (map str)))
        width       (->> skein-paths (map trim-dot) (map count) (apply max))
        test-totals (map #(run-tests project width %) skein-paths)
        totals      (apply merge-with + test-totals)]
    (println "Results:" (compose-totals totals))
    (when (pos? (+ (:new totals) (:error totals)))
      (perr [:yellow "Run " [:bold "dgt skein " [:italic "<file>"]] " to run the skein UI to investigate errors."])
      (cli/exit 1))))
