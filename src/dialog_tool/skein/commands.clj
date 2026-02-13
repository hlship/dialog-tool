(ns dialog-tool.skein.commands
  (:require [clojure.string :as string]
            [dialog-tool.util :as util]
            [clj-commons.ansi :refer [perr]]
            [net.lewisship.cli-tools :as cli :refer [defcommand abort]]
            [dialog-tool.skein.process :as sk.process]
            [babashka.process :as p]
            [babashka.fs :as fs]))

(defn- in-root
  [f]
  (if (fs/absolute? f)
    f
    (-> (util/find-root)
        (fs/file f)
        fs/absolutize
        str)))

(defn- start-skein-service!
  [params]
  (perr [:faint "Starting Clojure process ..."])
  ;; Need to collect the Java classpath used for dialog-tool.
  (let [proc (p/process {:dir (-> (util/find-root) fs/file)
                         :out :string}
                        "clojure -Aclojure -Srepro -Spath")
        {:keys [out exit]} @proc]
    (when-not (zero? exit)
      (cli/abort exit "Failure collecting Clojure process dependencies"))
    (let [paths      (-> out
                         string/trim
                         (string/split #":"))
          class-path (->> paths
                          (map in-root)
                          (string/join ":"))
          {:keys [skein-path seed engine]} params
          args       (cond-> ["java"
                              "--class-path" class-path
                              "clojure.main"
                              "-m" "dialog-tool.skein.main"]
                       seed (conj "--seed" (str seed))
                       engine (conj "--engine" (name engine))
                       true (conj skein-path))]
      (p/exec {:cmd args}))))

(defcommand skein
  "Run the Skein UI for an existing skein file."
  [:args
   skein ["SKEIN" "Path to skein file to run; defaults to default.skein"
          :optional true]]
  (let [skein-path (or skein "default.skein")]
    (when-not (fs/exists? skein-path)
      (abort [:bold skein-path] " does not exist"))
    (start-skein-service! {:skein-path skein-path})))

(defcommand new-skein
  "Create a new skein, and run the Skein UI.

  Note: the frotz engine has output formatting issues and is not yet ready for use."
  [seed [nil "--seed NUMBER" "Random number generator seed to use"
         :parse-fn parse-long
         :validate [some? "Not a number"
                    pos-int? "Must be at least one"]]
   engine (cli/select-option "-e" "--engine NAME" "Engine to use:"
                             sk.process/engines
                             :default :dgdebug)
   :args
   skein ["SKEIN" "Path to skein file to create; defaults to default.skein"
          :optional true]]
  (let [skein-path (or skein "default.skein")]
    (when (fs/exists? skein-path)
      (abort [:bold skein-path] " already exists"))
    (start-skein-service! {:skein-path skein-path :seed seed :engine engine})))
