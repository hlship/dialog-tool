(ns dialog-tool.skein.commands
  (:require [net.lewisship.cli-tools :as cli :refer [defcommand abort]]
            [clojure.java.browse :as browse]
            [dialog-tool.skein.service :as service]
            [dialog-tool.project-file :as pf]
            [clj-commons.ansi :refer [pout]]
            [dialog-tool.skein.process :as sk.process]
            [babashka.fs :as fs]))

(defn- start-skein-service!
  [skein-path start-opts]
  (let [project (pf/read-project)
        {:keys [port]} (service/start! project skein-path start-opts)
        url     (str "http://localhost:" port)]
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
