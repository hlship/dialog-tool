(ns dialog-tool.main
  (:require [dialog-tool.env :as env]
            [net.lewisship.cli-tools :as cli])
  (:gen-class))

(defn option-handler
  [{:keys [version debug]} _dispatch-options callback]
  (when version
    (cli/abort 0 (env/version)))

  (binding [env/*debug* (boolean debug)]
    (callback)))

(defn -main
  [& args]
  (cli/dispatch {:tool-name            "dgt"
                 :namespaces           '[dialog-tool.commands
                                         net.lewisship.cli-tools.completions]
                 :arguments            args
                 :extra-tool-options   [["-v" "--version" "Show version information and exit"]
                                        ["-d" "--debug" "Enable developer output"]]
                 :tool-options-handler option-handler}))
