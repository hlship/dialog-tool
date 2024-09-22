(ns dialog-tool.util
  (:require [clj-commons.ansi :refer [perr]]
            [net.lewisship.cli-tools :as cli]))

(defn fail
  [& msg]
  (perr [:red
         [:bold "ERROR: "]
         msg])
  (cli/exit -1))

