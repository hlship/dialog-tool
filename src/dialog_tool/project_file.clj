(ns dialog-tool.project-file
  (:require [clojure.edn :as edn]
            [babashka.fs :as fs]
            [dialog-tool.util :refer [fail]]
            [clojure.string :as string]))

(defn read-project
  ;; 0 arity is normal, 1 arity is just for testing purposes
  ([]
   (read-project "."))
  ([dir]
   (let [dir' (fs/path (or dir "."))
         path (fs/path dir '"dialog.edn")]
     (or (fs/exists? path)
         (fail (str path) " does not exist"))
     (-> path
         fs/file
         slurp
         edn/read-string
         (assoc ::dir dir')))))

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
   (let [{::keys [dir]
          :keys  [sources]} project
         {:keys [story debug library]} sources
         globs (concat
                 story
                 (when debug? debug)
                 library)]
     (->> globs
          (mapcat #(expand-source dir %))
          (map str)))))

(defn ^String relative-path
  [project path]
  (if-not (string/starts-with? path "/")
    (str (::dir project) "/" path)
    path))

(defn test-skein-paths
  ;; TODO: Change this to just find any .skein files in the project root.
  [project]
  (let [{:keys [test-skeins]
         :or   {test-skeins ["game.skein"]}} project]
    (map #(relative-path project %) test-skeins)))




