(ns dialog-tool.template
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [selmer.parser :as s]
            [babashka.process :as p]
            [clj-commons.ansi :refer [perr]]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import (java.nio.file Path)))

(defn- subpath
  [^Path p]
  (.subpath p 1 (.getNameCount p)))

(defn- copy
  [target source context]
  (perr [:cyan "  " (subpath target)])
  (fs/create-dirs (fs/parent target))
  (let [content (s/render-file source context)]
    (with-open [w (-> target
                      fs/create-file
                      fs/file
                      io/writer
                      )]
      (.write w content))))

(defn- file-copy
  [from to]
  (perr [:cyan "  " (subpath to)])
  (fs/create-dirs (fs/parent to))
  (fs/copy from to))

(defn create-from-template
  [dir opts]
  (perr [:cyan "Creating " dir " ..."])
  (let [{:keys [project-name]} opts
        dir'        (fs/path dir)
        brew-root   (-> (p/sh "brew --prefix")
                        :out
                        string/trim)
        dialog-root (fs/path brew-root "share" "dialog-if")]

    (copy (fs/path dir' "dialog.edn")
          "template/dialog.edn"
          opts)

    (copy (fs/path dir' "src" "meta.dg")
          "template/meta.dg"
          {:ifid (-> (random-uuid) str str/upper-case)})

    (copy (fs/path dir' "src" (str project-name ".dg"))
          "template/project.dg"
          {})

    (file-copy (fs/path dialog-root "stdlib.dg")
               (fs/path dir' "lib" "dialog" "stdlib.dg"))

    (file-copy (fs/path dialog-root "stddebug.dg")
               (fs/path dir' "lib" "dialog" "stddebug.dg"))

    (perr "\nChange to directory " [:bold dir] " to begin work")
    (perr [:bold "dgt debug"] " to run the project in the Dialog debugger")
    (perr [:bold "dgt skein"] " to open a web browser to the Skein UI")
    (perr [:bold "dgt help"] " for other options")))

