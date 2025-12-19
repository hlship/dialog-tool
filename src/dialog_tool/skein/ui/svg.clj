(ns dialog-tool.skein.ui.svg 
  (:require [clojure.java.io :as io]
            [huff2.core :as h]))

(defn svg [label & body]
  [:svg {:xmlns      "https://www.w3.org/2000/svg"
         :fill       "currentColor"
         :class      "shrink-0 w-5 h-5 me-2"
         :aria-label label
         :view-box   "0 0 24 24"}
   body])

(defn- read-svg
  [name]
  (let [file (str "icons/" name ".svg")]
    (-> file io/resource slurp h/raw-string)))

(def icon-play 
  (read-svg "play-solid"))

(def icon-save
  (read-svg "folder-arrow-down-solid"))

(def icon-dots-vertical
  (read-svg "ellipsis-vertical-solid"))