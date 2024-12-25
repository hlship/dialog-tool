(ns dialog-tool.bundle
  "Functions to assist with bundling a release, which is a Zip archive of the compiled project,
  plus a web-page wrapper to describe the project, plus a second compilation (if necessary)
  to AAMachine to support execution in the web browser."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clj-commons.ansi :refer [perr]]
            [clj-commons.humanize :as h]
            [clojure.string :as string]
            [dialog-tool.skein.process :as sk.process]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.template :as t]
            [dialog-tool.skein.file :as file]
            [dialog-tool.util :refer [fail]]
            [dialog-tool.build :as build])
  (:import (java.io IOException)))

(defn- extract-text
  [process command]
  (let [response (sk.process/send-command! process command)
        lines (string/split-lines response)]
    (->> lines
         (drop 1)
         butlast
         (string/join " "))))

(defn- extract-story-info
  [project]
  (let [process (sk.process/start-debug-process! project 0)
        _ (sk.process/read-response! process)
        info (reduce (fn [m k]
                       (assoc m k
                                (extract-text process (str "(story " (name k) ")"))))
                     nil
                     [:title :author :ifid :noun :blurb])
        response (sk.process/send-command! process "(story release $)")
        [_ release] (re-find #"\(story release (\d+)\)" response)]
    (sk.process/kill! process)
    (assoc info :release release)))

(defn- extract-walkthrough
  "Returns the text of the walkthough, or nil."
  [project]
  (let [{:keys [walkthrough-skein]
         :or   {walkthrough-skein "default.skein"}} project]
    (when walkthrough-skein
      (let [tree (try
                   (file/load-tree walkthrough-skein)
                   (catch IOException e
                     (fail (ex-message e))))
            knot (tree/find-by-label tree "WALKTHROUGH")]
        (when knot
          (->> (tree/knots-from-root tree (:id knot))
               (map :response)
               (reduce str)))))))

(defn bundle-project
  [project]
  (let [project-name (:name project)
        compiled-path (build/build-project project nil)
        aa-path (if (= :aa (:format project))
                  compiled-path
                  (build/build-project project {:format :aa}))
        compiled-name (fs/file-name compiled-path)
        story (extract-story-info project)
        zip-file (fs/path "." "out" (str project-name "-" (:release story) ".zip"))
        bundle-path (fs/path "." "out" "web")]

    ;; DELETE output tree first (for aambundle)

    (when (fs/exists? bundle-path)
      (fs/delete-tree bundle-path))

    ;; Must be first, because it will fail if the output directory already exists.
    (p/shell "aambundle --target web"
             "--output" (str bundle-path)
             (str aa-path))
    (perr [:cyan "  out/web/resources/..."])

    ;; Override for a wide play area in the browser
    ;; TODO: Could we edit the file instead?
    (t/copy-binary "bundle/play.css" (fs/path bundle-path "resources" "style.css"))

    (run! (fn [source]
            (t/copy-binary (str "bundle/" source)
                           (fs/path bundle-path source)))
          ["introduction-to-if.pdf"
           "play-if-card.pdf"
           "style.css"])

    (t/file-copy compiled-path (fs/path bundle-path compiled-name))
    (t/file-copy "cover.png" (fs/path bundle-path "cover.png"))

    (perr [:cyan "  out/web/cover-small.jpg"])
    (p/shell "magick cover.png -resize 120 out/web/cover-small.jpg")

    (let [walkthrough (extract-walkthrough project)
          walkthrough-description (when walkthrough
                                    (let [path (fs/path bundle-path "walkthrough.txt")]
                                      (t/copy-string walkthrough path)
                                      ;; The description (part of index.html):
                                      (str "text "
                                           (-> path fs/size h/filesize))))]
      (t/copy "bundle/index.html"
              {:story                   story
               :story-file              compiled-name
               :story-file-description  (str
                                          (-> project :format name)
                                          " "
                                          (-> compiled-path
                                              fs/size
                                              h/filesize))
               :walkthrough-description walkthrough-description}
              (fs/path bundle-path "index.html")))

    (t/setup-target zip-file)
    (fs/delete-if-exists zip-file)
    (fs/create-file zip-file)
    (fs/zip zip-file ["out/web"] {:root "out/web"})))
