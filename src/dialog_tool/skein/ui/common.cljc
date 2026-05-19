(ns dialog-tool.skein.ui.common
  (:require [clojure.string :as string]))

(defn normalize-input
  [s]
  (-> s str string/trim (string/replace #"\s+" " ")))


(defn setup-source-error
  [session error]
  (-> session
      (dissoc :error)
      (assoc :modal {:type  :source-error
                     :error error})))

(defn maybe-apply-source-error
  [session]
  (if-let [error (:error session)]
    (setup-source-error session error)
    session))
