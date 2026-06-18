(ns dialog-tool.helpers
  (:require [dialog-tool.skein.tree :as tree]))

(defn make-tree
  "Helper to create a fresh tree for testing."
  []
  (tree/new-tree :dgdebug 12345))


(defn response [text]
  {:content text
   :prompt  :line})
