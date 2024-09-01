(ns dialog-tool.util
  (:require [clj-commons.ansi :refer [perr]]
            [net.lewisship.cli-tools :as cli]))

(def *debug (atom false))

(def debug-opt
  ["-d" "--debug" "Enable dialog-tool debugging output"
   :update-fn (fn [_]
                (reset! *debug true))])

(defn fail
  [& msg]
  (perr [:red
         [:bold "ERROR: "]
         msg])
  (cli/exit -1))

