(ns dialog-tool.template
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [selmer.parser :as s]
            [babashka.process :as p]
            [clj-commons.ansi :refer [perr]]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn- copy
  [target source]
  (perr [:cyan "  " source])
  (let [f (fs/file target source)]
    (fs/create-file f)
    (io/copy (-> (str "template/" source)
                 io/resource
                 io/input-stream)
             f)))

(defn- file-copy
  [from to]
  (perr [:cyan "  " (fs/file-name from)])
  (fs/copy from to))

(defn create-from-template
  [dir _opts]
  (perr [:cyan "Creating " dir " ..."])
  (let [dir'        (fs/path dir)
        brew-root   (-> (p/sh "brew --prefix")
                        :out
                        string/trim)
        dialog-root (fs/path brew-root "share" "dialog-if")
        meta        (s/render-file "template/meta.dg"
                                   {:ifid (-> (random-uuid)
                                              str
                                              (str/upper-case))})]
    (fs/create-dirs (fs/path dir' "src"))
    (fs/create-dirs (fs/path dir' "lib" "dialog"))

    (copy dir' "dialog.edn")

    (perr [:cyan "  meta.dg"])
    (with-open [w (-> (fs/file dir' "src" "meta.dg")
                      fs/create-file
                      fs/file
                      io/writer)]
      (.write w meta))

    (copy (fs/path dir' "src") "project.dg")

    (file-copy (fs/path dialog-root "stdlib.dg")
               (fs/path dir' "lib" "dialog" "stdlib.dg"))

    (file-copy (fs/path dialog-root "stddebug.dg")
               (fs/path dir' "lib" "dialog" "stddebug.dg"))


    (perr "\nChange to " [:bold dir] " to begin work")
    (perr [:bold "dgt debug"] " to run the game in the Dialog debugger")
    (perr [:bold "dgt skein"] " to open a web browser to the Skein UI")
    (perr [:bold "dgt help"] " for other options")))

