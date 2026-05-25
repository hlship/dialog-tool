(ns dialog-tool.skein.ui.common
  (:require [clojure.string :as string]
            [hyper.core :as h]))

(defn normalize-input
  [s]
  (-> s str string/trim (string/replace #"\s+" " ")))

(defn setup-source-error
  [session error]
  (reset! (h/global-cursor :modal)
          {:type  :source-error
           :error error})
  (dissoc session :error))

(defn maybe-apply-source-error
  [session]
  (if-let [error (:error session)]
    (setup-source-error session error)
    session))
