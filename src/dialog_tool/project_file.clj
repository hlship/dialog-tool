(ns dialog-tool.project-file
  (:require [clojure.edn :as edn]
            [babashka.fs :as fs]
            [dialog-tool.util :refer [fail]]
            [clojure.string :as string]))

(defn read-project
  [dir]
  (let [dir'    (fs/path (or dir "."))
        path    (fs/path dir' "dialog.edn")
        _       (or (fs/exists? path)
                    (fail (str path) " does not exist"))
        content (-> path
                    fs/file
                    slurp
                    edn/read-string)]
    {:dir     dir'
     :content content}))

(defn- expand-source
  [dir glob]
  (if (string/starts-with? glob "/")
    [glob]
    (fs/glob dir glob)))

(defn expand-sources
  ([project]
   (expand-sources project nil))
  ([project {:keys [debug?]
             :or   {debug? true}}]
   (let [{:keys [dir content]} project
         {:keys [sources]} content
         {:keys [story debug library]} sources
         globs (concat
                 story
                 (when debug? debug)
                 library)]
     (->> globs
          (mapcat #(expand-source dir %))
          (map str)))))



