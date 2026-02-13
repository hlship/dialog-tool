(ns dialog-tool.skein.main
  ;; Single-command namespace used to launch the skein.
  (:require [clj-commons.ansi :refer [pout]]
            [clojure.java.browse :as browse]
            [babashka.fs :as fs]
            [dialog-tool.skein.process :as sk.process]
            [dialog-tool.skein.service :as service]
            [net.lewisship.cli-tools :as cli]))

(cli/defcommand -main
  "Launches the Skein."
  [seed [nil "--seed NUMBER" "Random number generator seed to use"
         :parse-fn parse-long
         :validate [some? "Not a number"
                    pos-int? "Must be at least one"]]
   engine (cli/select-option nil "--engine NAME" "Engine to use:"
                             sk.process/engines
                             :default :dgdebug)
   :args
   skein-path ["PATH" "Path to the skein file"]
   :command "main"]
  (let [{:keys [port]} (service/start! nil skein-path {:engine engine :seed seed})
        url (str "http://localhost:" port)]
    (pout [:bold (if (fs/exists? skein-path) "Loading" "Creating")
           " " skein-path " ..."])
    (pout [:faint "Skein service started on port " port " ..."])
    (pout "Hit " [:bold "Ctrl+C"] " when done")
    (browse/browse-url url)
    ;; Hang forever
    @(promise)))
