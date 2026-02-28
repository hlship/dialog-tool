(ns dialog-tool.bundle
  "Functions to assist with bundling a release, which is a Zip archive of the compiled project,
  plus a web-page wrapper to describe the project, plus a second compilation (if necessary)
  to AAMachine to support execution in the web browser."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clj-commons.ansi :refer [perr]]
            [clj-commons.humanize :as h]
            [dialog-tool.project-file :as pf]
            [net.lewisship.cli-tools :as cli]
            [clojure.string :as string]
            [dialog-tool.skein.ui.ansi :as ansi]
            [dialog-tool.skein.process :as sk.process]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.template :as t]
            [dialog-tool.skein.file :as file]
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
  (let [process (sk.process/start-debug-process! (pf/root-dir project) 0)
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
  "Returns the text of the walkthrough, or nil."
  [project]
  (let [{:keys [walkthrough-skein]
         :or {walkthrough-skein "default.skein"}} project]
    (when (and walkthrough-skein
               (fs/exists? walkthrough-skein))
      (let [tree (try
                   (file/load-tree walkthrough-skein)
                   (catch IOException e
                     (cli/abort e)))
            knot (tree/find-by-label tree "WALKTHROUGH")]
        (when knot
          (->> (tree/knots-from-root tree (:id knot))
               ;; Skip transcript comments (commands starting with *)
               (remove #(some-> (:command %) (string/starts-with? "*")))
               (map #(ansi/strip-ansi (:response %)))
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
        bundle-out-dir (fs/path "." "out" "web")]

    ;; DELETE output tree first (for aambundle)

    (when (fs/exists? bundle-out-dir)
      (fs/delete-tree bundle-out-dir))

    ;; Must be first, because it will fail if the output directory already exists.
    (p/shell (pf/command-path project "aambundle")
             "--target" "web"
             "--output" (str bundle-out-dir)
             (str aa-path))
    (perr [:cyan "  out/web/resources/..."])

    ;; Overwrite the default style.css with this one
    (t/copy-file "bundle/play.css"
                 (fs/path bundle-out-dir "resources" "style.css"))
    (t/copy-file "bundle/style.css"
                 (fs/path bundle-out-dir "style.css"))

    ;; These are on the tool's classpath:
    (doseq [source ["introduction-to-if.pdf"
                    "play-if-card.pdf"]]
      (t/copy-resource (str "bundle/" source)
                       (fs/path bundle-out-dir source)))

    (t/copy-file compiled-path (fs/path bundle-out-dir compiled-name))
    (t/copy-file "cover.png" (fs/path bundle-out-dir "cover.png"))

    (perr [:cyan "  out/web/cover-small.jpg"])
    (p/shell "magick cover.png -resize 120 out/web/cover-small.jpg")

    (let [walkthrough (extract-walkthrough project)
          walkthrough-description (when walkthrough
                                    (let [path (fs/path bundle-out-dir "walkthrough.txt")]
                                      (t/copy-string walkthrough path)
                                      ;; The description (part of index.html):
                                      (str "text "
                                           (-> path fs/size h/filesize))))]
      (t/copy-rendered "bundle/index.html"
                       {:story story
                        :story-file compiled-name
                        :story-file-description (str
                                                 (-> project :format name)
                                                 " "
                                                 (-> compiled-path
                                                     fs/size
                                                     h/filesize))
                        :walkthrough-description walkthrough-description}
                       (fs/path bundle-out-dir "index.html")))

    (t/setup-target zip-file)
    (fs/delete-if-exists zip-file)
    (fs/create-file zip-file)
    (fs/zip zip-file ["out/web"] {:root "out/web"})))
