(ns dialog-tool.project-file
  (:require [clojure.edn :as edn]
            [net.lewisship.cli-tools :refer [abort]]
            [babashka.fs :as fs])
  (:import (java.nio.file Path)))

(defn read-project
  ;; 0 arity is normal, 1 arity is just for testing purposes
  ([]
   (read-project ""))
  ([root-dir]
   (let [root-dir' (fs/path (or root-dir ""))
         path (fs/path root-dir '"dialog.edn")]
     (when-not (fs/exists? path)
       (abort (str path) " does not exist"))
     (try
       (-> path
           fs/file
           slurp
           edn/read-string
           (assoc ::root-dir root-dir'))
       (catch Throwable t
         (abort "Could not read " [:bold path] ": "
               (ex-message t)))))))

(defn- expand-source
  [root-dir source]
  (if (instance? Path source)
    [source]
    (sort (fs/glob (fs/path root-dir source) "*.dg" {:follow-links true}))))

(defn expand-sources
  ([project]
   (expand-sources project nil))
  ([project {:keys [debug? pre-patch]}]
   (let [{::keys [root-dir]
          :keys  [sources]} project
         {:keys [main debug library]} sources
         sources (concat
                   pre-patch
                   main
                   (when debug? debug)
                   library)]
     (->> sources
          (mapcat #(expand-source root-dir %))
          (map str)))))

(defn root-dir
  ^Path [project]
  (::root-dir project))
