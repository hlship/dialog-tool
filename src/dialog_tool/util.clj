(ns dialog-tool.util
  (:require [babashka.fs :as fs]
            [clj-commons.ansi :refer [perr]]
            [net.lewisship.cli-tools :as cli]))

(defn fail
  [& msg]
  (perr [:red
         [:bold "ERROR: "]
         msg])
  (cli/exit -1))

(defn root-path
  "Construct a Path from the root folder of the dialog tool (the directory containing
  the dgt script) to a file."
  [& terms]
  (let [root
        (-> *file*
            fs/path
            fs/parent)]
    (apply fs/path root terms)))

