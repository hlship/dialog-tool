(ns dialog-tool.main
  (:require [dialog-tool.env :as env]
            [net.lewisship.cli-tools :as cli])
  (:gen-class))

(defn option-handler
  [{:keys [debug]} _dispatch-options callback]

  (binding [env/*debug* (boolean debug)]
    (callback)))

(defn -main
  [& args]
  (cli/dispatch {:tool-name "dgt"
                 :namespaces '[dialog-tool.commands
                               net.lewisship.cli-tools.completions]
                 :version (env/version)
                 :groups {"skein" {:doc        "Skein UI and testing commands"
                                   :namespaces '[dialog-tool.skein.commands]}}
                 :arguments args
                 :extra-tool-options [["-d" "--debug" "Enable developer output"]]
                 :tool-options-handler option-handler})
  (cli/abort 0)) 
