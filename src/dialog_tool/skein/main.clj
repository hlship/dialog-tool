(ns dialog-tool.skein.main
  "Alternate, internal program entrypoint used when a Java process is created to run the Skein."
  (:require [clj-commons.ansi :refer [pout]]
            [clojure.java.browse :as browse]
            [babashka.fs :as fs]
            [dialog-tool.env :as env]
            [dialog-tool.skein.service :as service]
            [clojure.edn :as edn]))

(defn launch
  [params]
  (let [{:keys [skein-path debug]} params
        _   (alter-var-root #'env/*debug* (constantly debug))
        {:keys [port]} (service/start! nil params)
        url (str "http://localhost:" port)]
    (pout [:bold (if (fs/exists? skein-path) "Loading" "Creating")
           " " skein-path " ..."])
    (pout [:faint "Skein service started on port " port " ..."])
    (pout "Hit " [:bold "Ctrl+C"] " when done")
    (browse/browse-url url)
    ;; Hang forever
    @(promise)))

(defn -main
  "Launches the Skein."
  [params & _args]
  (launch (edn/read-string params)))
