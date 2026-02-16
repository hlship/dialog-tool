(ns dialog-tool.env
  (:require [clojure.java.io :as io]
            [clojure.string :as string]))

(def ^:dynamic *debug* false)

(defn version
  []
  (if-let [url (io/resource "version.text")]
    (-> url slurp string/trim)
    "DEV"))
