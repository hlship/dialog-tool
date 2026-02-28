(ns dialog-tool.skein.commands
  (:require [babashka.fs :as fs]
            [clj-commons.ansi :as ansi :refer [pout perr]]
            [clojure.java.browse :as browse]
            [dialog-tool.skein.file :as sk.file]
            [dialog-tool.skein.process :as sk.process]
            [dialog-tool.skein.service :as service]
            [dialog-tool.skein.session :as s]
            [dialog-tool.skein.tree :as tree]
            [net.lewisship.cli-tools :as cli :refer [defcommand abort]]))

(defn- compose-totals
  [totals]
  (ansi/compose [:green (:ok totals)] "/"
                [:yellow (:new totals)] "/"
                [:red (:error totals)]))

(defn- run-tests
  [width quiet? skein-path]
  (let [tree (sk.file/load-tree skein-path)
        {:keys [engine seed]
         :or {engine :dgdebug}} (:meta tree)
        start-process #(sk.process/start-process! nil engine seed)
        session (s/create-loaded! start-process skein-path tree)
        leaf-ids (->> tree
                      tree/leaf-knots
                      (map :id))
        test-leaf (fn [session id]
                    (when-not quiet?
                      (print ".") (flush))
                    (s/do-replay-to! session id))
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

(defn- launch
  [params]
  (let [{:keys [skein-path]} params
        port (service/start! nil params)
        url (str "http://localhost:" port)]
    (pout [:bold (if (fs/exists? skein-path) "Loading" "Creating")
           " " skein-path " ..."])
    (pout [:faint "Skein service started on port " port " ..."])
    (pout "Hit " [:bold "Ctrl+C"] " when done")
    (browse/browse-url url)
    ;; Hang forever
    @(promise)))

(def ^:private port-opt ["-p" "--port NUMBER" "Port number for the Skein service"
                         :parse-fn parse-long
                         :validate [some? "Not a number"
                                    pos-int? "Must be at least one"]])

(defcommand test-skein
  "Use the available skein(s) to test the project.

  Outputs the number of correct knots, the number of new knots
  (no prior response), and the number of error knots (conflicting response).

  Exits with 0 if all knots are correct, or with 1 if there are any errors."
  [quiet? ["-q" "--quiet" "Minimize output"] :args
   skein-file ["SKEIN-FILE" "Path to single skein file to test"
               :optional true]
   :command "test"]
  (let [skein-paths (if skein-file
                      [skein-file]
                      (map str (fs/glob "" "*.skein")))
        _ (when-not (seq skein-paths)
            (abort "No skein files found"))
        width (->> skein-paths (map count) (apply max))
        test-totals (map #(run-tests width quiet? %) skein-paths)
        totals (apply merge-with + test-totals)
        pretty-totals (compose-totals totals)]
    (if quiet?
      (println pretty-totals)
      (println "Results:" (compose-totals totals)))
    (when (pos? (+ (:new totals) (:error totals)))
      (when-not quiet?
        (perr [:yellow "Run " [:bold "dgt skein run " [:italic "<file>"]] " to run the skein UI to investigate errors"]))
      (cli/exit 1))))

(defcommand run-skein
  "Run the Skein UI for an existing skein file."
  [port port-opt
   :args
   skein ["SKEIN" "Path to skein file to run; defaults to default.skein"
          :optional true]
   :command "run"]
  (let [skein-path (or skein "default.skein")]
    (when-not (fs/exists? skein-path)
      (abort [:bold skein-path] " does not exist"))
    (launch {:skein-path skein-path
             :port port})))

(defcommand new-skein
  "Create a new skein, and run the Skein UI.

  Note: the frotz engine has output formatting issues and is not yet ready for use."
  [seed [nil "--seed NUMBER" "Random number generator seed to use"
         :parse-fn parse-long
         :validate [some? "Not a number"
                    pos-int? "Must be at least one"]]
   port port-opt
   engine (cli/select-option "-e" "--engine NAME" "Engine to use:"
                             sk.process/engines
                             :default :dgdebug)
   :args
   skein ["SKEIN" "Path to skein file to create; defaults to default.skein"
          :optional true]
   :command "new"]
  (let [skein-path (or skein "default.skein")]
    (when (fs/exists? skein-path)
      (abort [:bold skein-path] " already exists"))
    (launch {:skein-path skein-path
             :seed seed
             :engine engine
             :port port})))
