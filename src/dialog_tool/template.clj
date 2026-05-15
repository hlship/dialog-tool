(ns dialog-tool.template
  (:require [babashka.fs :as fs]
            [clj-commons.ansi :refer [perr]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [selmer.parser :as s])
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

(defn copy-string
  "Write the content of a string to a file."
  [^String s target]
  (setup-target target)
  (with-open [w (-> target maybe-create fs/file io/writer)]
    (.write w s)))

(defn copy-rendered
  "Copies a selmer template; the template is rendered and the result
   written to target."
  [resource-path context target]
  (copy-string (s/render-file resource-path context) target))

(defn copy-resource
  "Copies binary resource path (on the classpath) to a target path."
  [resource-path target]
  (setup-target target)
  (with-open [in  (-> resource-path io/resource io/input-stream)
              out (-> target maybe-create fs/file io/output-stream)]
    (io/copy in out)))

(defn copy-file
  "Copies a file to a target path."
  [from target]
  (setup-target target)
  (fs/copy from target {:replace-existing true}))

(defn- conj-source
  "Adds source-entry to the source-key vector in sources, avoiding duplicates."
  [sources source-key source-entry]
  (update sources source-key
          (fn [v] (let [entries (or v [])]
                    (if (some #{source-entry} entries)
                      entries
                      (conj entries source-entry))))))

(defn- add-source
  "Renders a Selmer template to src/file-name and adds the appropriate
  path to :main in sources. When flat?, the individual file path is added;
  otherwise the directory."
  [sources dir flat? resource-path context file-name]
  (let [{:keys [category]
         :or   {category :main}} context
        target-dir   (cond flat?
                           "src"

                           (= category :main)
                           "src"

                           :else
                           "test")
        target-path  (str target-dir "/" file-name)
        source-entry (if flat? target-path target-dir)]
    (copy-rendered resource-path context (fs/path dir target-path))
    (conj-source sources category source-entry)))

(defn- add-lib
  "Copies a classpath resource to the project. When flat?, the file goes directly
  into base-dir; otherwise it goes into base-dir/sub-dir. The appropriate path
  is added to source-key in sources."
  [sources dir flat? resource-path source-key sub-dir file-name]
  (let [target-dir   (if flat?
                       "src"
                       (str "lib/" sub-dir))
        target-path  (str target-dir "/" file-name)
        source-entry (if flat? target-path target-dir)]
    (copy-resource resource-path (fs/path dir target-path))
    (conj-source sources source-key source-entry)))

(defn- format-sources
  "Formats a vector of source paths as a quoted, space-separated string
  for use in the dialog.edn template."
  [paths]
  (->> paths
       (map pr-str)
       (string/join " ")))

(defn create-from-template
  [dir opts]
  (perr [:cyan "Creating " dir " ..."])
  (let [{:keys [project-name flat?]} opts
        dir' (fs/path dir)]

    (let [sources (-> {}

                      ;; Source files rendered from templates
                      (add-source dir' flat? "template/meta.dg"
                                  {:ifid (-> (random-uuid) str string/upper-case)}
                                  "meta.dg")
                      (add-source dir' flat? "template/project.dg"
                                  {}
                                  (str project-name ".dg"))
                      (add-source dir' flat? "template/unit-tests.dg"
                                  {:category :test}
                                  "unit-tests.dg")

                      ;; Library sources (placement depends on flat?)
                      (add-lib dir' flat? "template/stdlib.dg" :library "dialog" "stdlib.dg")
                      (add-lib dir' flat? "template/stddebug.dg" :debug "dialog/debug" "stddebug.dg")
                      (add-lib dir' flat? "template/unit.dg" :test "dialog/test" "unit.dg"))]

      (copy-rendered "template/dialog.edn"
                     {:project-name project-name
                      :main (format-sources (:main sources))
                      :test (format-sources (:test sources))
                      :debug (format-sources (:debug sources))
                      :library (format-sources (:library sources))}
                     (fs/path dir' "dialog.edn")))

    ;; Non-source files
    (copy-resource "template/.gitignore" (fs/path dir' ".gitignore"))
    (copy-resource "template/default-cover.png" (fs/path dir' "cover.png"))
    (doseq [f ["index.html" "play.css" "style.css"]]
      (copy-resource (str "bundle/" f) (fs/path dir' "bundle" f)))

    (perr "\nChange to directory " [:bold dir] " to begin work")
    (perr [:bold "dgt debug"] " to run the project in the Dialog debugger")
    (perr [:bold "dgt skein new"] " to open a web browser to the Skein UI")
    (perr [:bold "dgt help"] " for other options")))
