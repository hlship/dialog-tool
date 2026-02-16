(ns dialog-tool.skein.start
  (:require [clojure.string :as string]
            [dialog-tool.env :as env]
            [clj-commons.ansi :refer [perr]]
            [babashka.process :as p]))

(defn start-service!
  "Starts the Skein with the given params; for Babashka, this launches a Java process, 
  but if started with Java, just runs the code directly."
  [params]
  (let [params' (assoc params :debug env/*debug*)]
    #?(:bb
       (do
         (perr [:faint "Starting Clojure process ..."])
         (let [class-path (System/getProperty "java.class.path")
               ;; First entry in class-path is the Uberjar (when deployed)
               [uberjar] (string/split class-path #":" 1)]
           (let [args ["java"
                       "--class-path" uberjar
                       "clojure.main"
                       "-m" "dialog-tool.skein.main"
                       (pr-str (assoc params' :debug env/*debug*))]]
             (p/exec {:cmd args}))))
       :clj
       ((requiring-resolve 'dialog-tool.skein.main/launch) params'))))
