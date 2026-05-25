(ns dialog-tool.env
  (:require [clojure.java.io :as io]
            [clj-commons.ansi :refer [perr]]
            [clojure.string :as string]))

(def ^:dynamic *debug* false)

(defn version
  []
  (if-let [url (io/resource "dialog-tool-version.txt")]
    (-> url slurp string/trim)
    "DEV"))

(defn log-action
  "Logs a user action to stderr. parts are joined with spaces."
  [& parts]
  (perr "action: " (string/join " " parts)))

(defn debug-command
  [command]
  (when *debug*
    (perr [:cyan (string/join " " command)])))
