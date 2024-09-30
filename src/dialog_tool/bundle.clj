(ns dialog-tool.bundle
  "Functions to assist with bundling a release, which is a Zip archive of the compiled project,
  plus a web-page wrapper to describe the project, plus a second compilation (if necessary)
  to AAMachine to support execution in the web browser."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clj-commons.ansi :refer [perr]]
            [clojure.string :as string]
            [dialog-tool.skein.process :as sk.process]
            [dialog-tool.template :as t]
            [dialog-tool.build :as build]))

(defn- extract-text
  [process command]
  (let [response (sk.process/send-command! process command)
        lines    (string/split-lines response)]
    (->> lines
         (drop 1)
         butlast
         (string/join " "))))

(defn- extract-story-info
  [project]
  (let [process  (sk.process/start-debug-process! project 0)
        _        (sk.process/read-response! process)
        info     (reduce (fn [m k]
                           (assoc m k
                                  (extract-text process (str "(story " (name k) ")"))))
                         nil
                         [:title :author :ifid :noun :blurb])
        response (sk.process/send-command! process "(story release $)")
        [_ release] (re-find #"\(story release (\d+)\)" response)]
    (sk.process/kill! process)
    (assoc info :release release)))

(defn bundle-project
  [project opts]
  (let [project-name (:name project)
        compiled-path (build/build-project project opts)
        aa-path       (if (= :aa (:format project))
                        compiled-path
                        (build/build-project project {:format :aa}))
        compiled-name (fs/file-name compiled-path)
        story         (extract-story-info project)
        zip-file (fs/path "." "out" (str project-name "-" (:release story) ".zip"))
        bundle-path   (fs/path "." "out" "web")]

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

    (t/copy-binary "bundle/introduction-to-if.pdf" (fs/path bundle-path "introduction-to-if.pdf"))
    (t/copy-binary "bundle/style.css" (fs/path bundle-path "style.css"))
    (t/file-copy compiled-path (fs/path bundle-path compiled-name))

    ;; TODO: Make cover.png optional (adjust template when it is missing).
    (t/file-copy "cover.png" (fs/path bundle-path "cover.png"))

    (perr [:cyan "  out/web/cover-small.jpg"])
    (p/shell "magick cover.png -resize 120 out/web/cover-small.jpg")

    (t/copy "bundle/index.html"
            {:story                  story
             :story-file             compiled-name
             :story-file-description "[DESC]"}
            (fs/path bundle-path "index.html"))

    (t/setup-target zip-file)
    (fs/delete-if-exists zip-file)
    (fs/create-file zip-file)
    (fs/zip zip-file ["out/web"] {:root "out/web"})))
