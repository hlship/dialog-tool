(ns dialog-tool.main
  (:require [net.lewisship.cli-tools :as cli]))

(defn main
  [& args]
  (cli/dispatch {:namespaces '[dialog-tool.commands
                               net.lewisship.cli-tools.completions]
                 :arguments  args}))



