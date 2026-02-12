(ns dialog-tool.main
  (:require [net.lewisship.cli-tools :as cli])
  (:gen-class))

(defn -main
  [& args]
  (cli/dispatch {:tool-name  "dgt"
                 :namespaces '[dialog-tool.commands
                               dialog-tool.skein.commands
                               net.lewisship.cli-tools.completions]
                 :arguments  args}))
