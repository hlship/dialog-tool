(ns dialog-tool.skein.start
  (:require [clojure.string :as string]
            [dialog-tool.env :as env]
            [clj-commons.ansi :refer [perr]]
            [babashka.process :as p]))

(defn- launch-service
  [params]
  (perr [:faint "Starting Clojure process ..."])
  (let [class-path (System/getProperty "java.class.path")
        ;; First entry in class-path is the Uberjar (when deployed)
        [uberjar] (string/split class-path #":" 1)]
    (let [command ["java"
                   "--class-path" uberjar
                   "clojure.main"
                   "-m" "dialog-tool.skein.main"
                   (pr-str params)]]
      (env/debug-command command)
      (p/exec {:cmd command}))))

(defn start-service!
  "Starts the Skein with the given params; for Babashka, this launches a Java process, 
  but if started with Java, just runs the code directly."
  [params]
  (let [params' (assoc params :debug env/*debug*)]
    #?(:bb
       (launch-service params')
       :clj
       ((requiring-resolve 'dialog-tool.skein.main/launch) params'))))
