(ns dialog-tool.skein.ui.utils
  "Shared UI utility functions."
  (:require [clojure.string :as string]))

(defn classes
  "Combines multiple class strings into a single string, skipping nils,
  and reducing spaces to a single space."
  [& s]
  (-> (string/join " " s)
      (string/replace #"\s+" " ")
      string/trim))
