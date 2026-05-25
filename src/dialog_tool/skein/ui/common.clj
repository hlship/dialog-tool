(ns dialog-tool.skein.ui.common
  (:require [clojure.string :as string]
            [hyper.core :as h]))

(defn session-cursor
  []
  (h/global-cursor :session))

(defn modal-cursor
  []
  (h/global-cursor :modal))

(defn search-cursor
  []
  (h/global-cursor :search))

(defn normalize-input
  [s]
  (-> s str string/trim (string/replace #"\s+" " ")))

(defn setup-source-error
  [session error]
  (reset! (modal-cursor)
          {:type  :source-error
           :error error})
  (dissoc session :error))

(defn maybe-apply-source-error
  [session]
  (if-let [error (:error session)]
    (setup-source-error session error)
    session))
