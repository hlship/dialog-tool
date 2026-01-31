(ns dialog-tool.template
  (:require [babashka.fs :as fs]
            [selmer.parser :as s]
            [babashka.process :as p]
            [clj-commons.ansi :refer [perr]]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import (java.nio.file Path)))

(defn- subpath
  [^Path p]
  (.subpath p 1 (.getNameCount p)))

(defn setup-target
  [target]
  (perr [:cyan "  " (subpath target)])
  (fs/create-dirs (fs/parent target)))

(defn- maybe-create
  [f]
  (when-not (fs/exists? f)
    (fs/create-file f))
  f)

(defn copy-rendered
  "Copies a selmer template; the template is rendered and the result
   written to target."
  [resource-path context target]
  (setup-target target)
  (let [content (s/render-file resource-path context)]
    (with-open [w (-> target
                      maybe-create
                      fs/file
                      io/writer)]
      (.write w content))))

(defn copy-file
  "Copies a file to a target path."
  [from target]
  (setup-target target)
  (fs/copy from target {:replace-existing true}))

(defn copy-resource
  "Copies binary resource path (on the classpath) to a target path."
  [resource-path target]
  (setup-target target)
  (with-open [in (-> resource-path
                     io/resource
                     io/input-stream)
              out (-> target
                      maybe-create
                      fs/file
                      io/output-stream)]
    (io/copy in out)))

(defn copy-string
  "Write the content of a string to a file."
  [^String s target]
  (setup-target target)
  (with-open [w (-> target
                    maybe-create
                    fs/file
                    io/writer)]
    (.write w s)))

(defn create-from-template
  [dir opts]
  (perr [:cyan "Creating " dir " ..."])
  (let [{:keys [project-name]} opts
        dir' (fs/path dir)
        src-dir (fs/path dir "src")
        brew-root (-> (p/sh "brew --prefix")
                      :out
                      string/trim)
        dialog-root (fs/path brew-root "share" "dialog-if")
        bundle-dir (fs/path dir' "bundle")]

    (copy-rendered "template/dialog.edn"
                   opts
                   (fs/path dir' "dialog.edn"))

    (copy-rendered "template/meta.dg"
                   {:ifid (-> (random-uuid) str string/upper-case)}
                   (fs/path src-dir "meta.dg"))

    (copy-rendered "template/project.dg"
                   {}
                   (fs/path src-dir (str project-name ".dg")))

    (copy-file (fs/path dialog-root "stdlib.dg")
               (fs/path dir' "lib" "dialog" "stdlib.dg"))

    (copy-file (fs/path dialog-root "stddebug.dg")
               (fs/path dir' "lib" "dialog" "debug" "stddebug.dg"))

    (copy-resource "template/default-cover.png"
                   (fs/path dir' "cover.png"))

    ;; These are copied over so that a project can modify them as desired.
    (doseq [f ["index.html" "play.css" "style.css"]]
      (copy-resource (str "bundle/" f)
                     (fs/path bundle-dir f)))

    (perr "\nChange to directory " [:bold dir] " to begin work")
    (perr [:bold "dgt debug"] " to run the project in the Dialog debugger")
    (perr [:bold "dgt new-skein"] " to open a web browser to the Skein UI")
    (perr [:bold "dgt help"] " for other options")))
