(ns dialog-tool.skein.ui.svg 
  (:require [clojure.java.io :as io]
            [huff2.core :as h]))

(defn- read-svg*
  [name]
  (let [file (str "icons/" name ".svg")]
    (-> file io/resource slurp h/raw-string)))

(def read-svg (memoize read-svg*))

(def icon-play 
  (read-svg "play-solid"))

(def icon-save
  (read-svg "folder-arrow-down-solid"))

(def icon-dots-vertical
  (read-svg "ellipsis-vertical-solid"))

(def icon-undo
  (read-svg "arrow-uturn-left"))

(def icon-redo
  (read-svg "arrow-uturn-right"))

(def icon-quit
  (read-svg "x-circle-solid"))

(def icon-children
  (read-svg "command-line-solid"))