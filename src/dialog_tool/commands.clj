(ns dialog-tool.commands
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clj-commons.ansi :as ansi :refer [pout perr]]
            [clojure.string :as string]
            [dialog-tool.skein.file :as sk.file]
            [dialog-tool.skein.process :as sk.process]
            [dialog-tool.skein.session :as s]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.template :as template]
            [dialog-tool.bundle :as bundle]
            [net.lewisship.cli-tools :as cli :refer [defcommand abort]]
            [clojure.java.browse :as browse]
            [dialog-tool.build :as build]
            [dialog-tool.skein.service :as service]
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
        cmd (-> ["dgdebug" "--quit"]
                (into extra-args)
                (into (pf/expand-sources project {:debug? true})))
        *process (p/process {:cmd     cmd
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

(defn- start-skein-service!
  [skein-path start-opts]
  (let [project (pf/read-project)
        {:keys [port]} (service/start! project skein-path start-opts)
        url (str "http://localhost:" port "/index.html")]
    (pout [:bold (if (fs/exists? skein-path) "Loading" "Creating")
           " " skein-path " ..."])
    (pout [:faint "Skein service started on port " port " ..."])
    (pout "Hit " [:bold "Ctrl+C"] " when done")
    (browse/browse-url url)
    ;; Hang forever
    @(promise)))

(defcommand skein
  "Run the Skein UI for an existing skein file."
  [:args
   skein ["SKEIN" "Path to skein file to run; defaults to default.skein"
          :optional true]]
  (let [skein-path (or skein "default.skein")]
    (when-not (fs/exists? skein-path)
      (abort [:bold skein-path] " does not exist"))
    (start-skein-service! skein-path nil)))

(defcommand new-skein
  "Create a new skein, and run the Skein UI.

  Note: the frotz engine has output formatting issues and is not yet ready for use."
  [seed [nil "--seed NUMBER" "Random number generator seed to use"
         :parse-fn parse-long
         :validate [some? "Not a number"
                    pos-int? "Must be at least one"]]
   engine (cli/select-option "-e" "--engine NAME" "Engine to use:"
                             sk.process/engines
                             :default :dgdebug)
   :args
   skein ["SKEIN" "Path to skein file to create; defaults to default.skein"
          :optional true]]
  (let [skein-path (or skein "default.skein")]
    (when (fs/exists? skein-path)
      (abort [:bold skein-path] " already exists"))
    (start-skein-service! skein-path {:seed seed :engine engine})))

(defcommand build
  "Compile the project to a file ready to execute with an interpreter."
  [format (cli/select-option "-f" "--format FORMAT"
                             "Output format:"
                             #{:zblorb :z5 :z8 :aa})
   debug? debug-opt
   verbose ["-v" "--verbose" "Enable additional compiler output"]]
  (build/build-project (pf/read-project)
                       {:debug?   debug?
                        :verbose? verbose
                        :format   format}))

(defcommand bundle
  "Bundle the project into a Zip archive that can be deployed to a web host."
  []
  (bundle/bundle-project (pf/read-project)))

(defn- compose-totals
  [totals]
  (ansi/compose [:green (:ok totals)] "/"
                [:yellow (:new totals)] "/"
                [:red (:error totals)]))

(defn- run-tests
  [project width quiet? skein-path]
  (let [tree (sk.file/load-tree skein-path)
        {:keys [engine seed]
         :or   {engine :dgdebug}} (:meta tree)
        process (sk.process/start-process! project engine seed)
        session (-> (s/create-loaded! process skein-path tree)
                    (s/enable-undo false))
        leaf-ids (->> tree
                      tree/leaf-knots
                      (map :id))
        test-leaf (fn [session id]
                    (when-not quiet?
                      (print ".") (flush))
                    (s/replay-to! session id))
        spaces (- width (count skein-path))
        session' (do
                   (when-not quiet?
                     (printf "Testing %s%s: "
                             (apply str (repeat spaces " "))
                             skein-path))
                   (reduce test-leaf session leaf-ids))
        totals (s/totals session')]
    (when-not quiet?
      (print " ")
      (println (compose-totals totals)))
    totals))

(defcommand test-project
  "Use the available skein(s) to test the project.

  Outputs the number of correct knots, the number of new knots
  (no prior response), and the number of error knots (conflicting response).

  Exits with 0 if all knots are correct, or with 1 if there are any errors."
  [quiet? ["-q" "--quiet" "Minimize output"] :args
   skein-file ["SKEIN-FILE" "Path to single skein file to test"
               :optional true]
   :command "test"]
  (let [project (pf/read-project)
        skein-paths (if skein-file
                      [skein-file]
                      (map str (fs/glob "" "*.skein")))
        _ (when-not (seq skein-paths)
            (abort "No skein files found"))
        width (->> skein-paths (map count) (apply max))
        test-totals (map #(run-tests project width quiet? %) skein-paths)
        totals (apply merge-with + test-totals)
        pretty-totals (compose-totals totals)]
    (if quiet?
      (println pretty-totals)
      (println "Results:" (compose-totals totals)))
    (when (pos? (+ (:new totals) (:error totals)))
      (when-not quiet?
        (perr [:yellow "Run " [:bold "dgt skein " [:italic "<file>"]] " to run the skein UI to investigate errors"]))
      (cli/exit 1))))

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
        path (build/build-project project
                                  {:format format'
                                   :debug? debug?})
        command (if dumb? "dfrotz" "frotz")
        extra-args (when dumb?
                     ["-m" "-q"])
        args (concat extra-args
                     frotz-args
                     [(str path)])]
    (apply p/exec command args)))

(defcommand sources
  "Print the sources for the project in compilation order."
  [debug? debug-opt
   one? ["-1" "--single-line" "Output as a single line, colon-seperated"]]
  (let [project (pf/read-project)
        paths (pf/expand-sources project {:debug? debug?})]
    (if one?
      (println (string/join ":" paths))
      (let [longest (apply max (map count paths))]
        (doseq [path paths]
          (pout [{:font  :cyan
                  :width longest} path]))))))