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
  "Logs a user action to stderr. Remaining args are concatenated as string parts."
  [action & parts]
  (perr (apply str "action: " action parts)))

(defn debug-command
  [command]
  (when *debug*
    (perr [:cyan (string/join " " command)])))
