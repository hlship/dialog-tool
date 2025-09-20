(ns dialog-tool.main
  (:require [net.lewisship.cli-tools :as cli]))

(defn main
  [& args]
  (cli/dispatch {:tool-name  "dgt"
                 :namespaces '[dialog-tool.commands]
                 :arguments  args}))



