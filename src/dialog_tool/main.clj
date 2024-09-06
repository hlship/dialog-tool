(ns dialog-tool.main
  (:require [net.lewisship.cli-tools :as cli]))

(defn main
  [& args]
  (cli/dispatch {:tool-name  "dgt"
                 :flat       true
                 :namespaces '[dialog-tool.commands]
                 :arguments  args}))



