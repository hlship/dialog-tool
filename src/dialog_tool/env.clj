(ns dialog-tool.env
  (:require [clojure.java.io :as io]
            [clj-commons.ansi :refer [perr]]
            [clojure.string :as string]))

(def ^:dynamic *debug* false)

(defn version
  []
  (if-let [url (io/resource "version.text")]
    (-> url slurp string/trim)
    "DEV"))

(defn debug-command
  [command]
  (when *debug*
    (perr [:cyan (string/join " " command)])))
