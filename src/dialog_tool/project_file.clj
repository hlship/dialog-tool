(ns dialog-tool.project-file
  (:require [clojure.edn :as edn]
            [babashka.fs :as fs]
            [dialog-tool.util :refer [fail]]
            [clojure.string :as string])
  (:import (java.nio.file Path)))

(defn read-project
  ;; 0 arity is normal, 1 arity is just for testing purposes
  ([]
   (read-project ""))
  ([dir]
   (let [dir' (fs/path (or dir ""))
         path (fs/path dir '"dialog.edn")]
     (or (fs/exists? path)
         (fail (str path) " does not exist"))
     (try
       (-> path
           fs/file
           slurp
           edn/read-string
           (assoc ::dir dir'))
       (catch Throwable t
         (fail "Could not read " [:bold path] ": "
               (ex-message t)))))))

(defn- expand-source
  [dir glob]
  (cond
    (instance? Path glob)
    [(str glob)]

    (string/starts-with? glob "/")
    [glob]

    :else
    (fs/glob dir glob)))

(defn expand-sources
  ([project]
   (expand-sources project nil))
  ([project {:keys [debug? pre-patch]}]
   (let [{::keys [dir]
          :keys  [sources]} project
         {:keys [main debug library]} sources
         globs (concat
                 pre-patch
                 main
                 (when debug? debug)
                 library)]
     (->> globs
          (mapcat #(expand-source dir %))
          (map str)))))

(defn ^Path project-dir
  [project]
  (::dir project))



