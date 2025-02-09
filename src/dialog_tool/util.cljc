(ns dialog-tool.util
  (:require [babashka.fs :as fs]))

(defn root-path
  "Construct a Path from the root folder of the dialog tool (the directory containing
  the dgt script) to a file."
  [& terms]
  (let [root
        #?(:bb (-> *file*
                   fs/path
                   fs/parent)
           :clj (fs/path "."))]
    (apply fs/path root terms)))
