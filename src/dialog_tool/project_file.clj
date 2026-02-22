(ns dialog-tool.project-file
  (:require [clojure.edn :as edn]
            [net.lewisship.cli-tools :refer [abort]]
            [taoensso.timbre :refer [spy]]            
            [babashka.fs :as fs])
  (:import (java.nio ByteBuffer)
           (java.nio.file Path)
           (java.nio.file.attribute BasicFileAttributes FileTime)
           (java.security MessageDigest)))

(defn read-project
  ;; 0 arity is normal, 1 arity is just for testing purposes
  ([]
   (read-project ""))
  ([root-dir]
   (let [root-dir' (fs/path (or root-dir ""))
         path (fs/path root-dir '"dialog.edn")]
     (when-not (fs/exists? path)
       (abort (str path) " does not exist"))
     (try
       (-> path
           fs/file
           slurp
           edn/read-string
           (assoc ::root-dir root-dir'))
       (catch Throwable t
         (abort "Could not read " [:bold path] ": "
               (ex-message t)))))))

(defn- expand-source
  [root-dir source]
  (if (instance? Path source)
    [source]
    (sort (fs/glob (fs/path root-dir source) "*.dg" {:follow-links true}))))

(defn expand-sources
  ([project]
   (expand-sources project nil))
  ([project {:keys [debug? pre-patch]}]
   (let [{::keys [root-dir]
          :keys  [sources]} project
         {:keys [main debug library]} sources
         sources (concat
                   pre-patch
                   main
                   (when debug? debug)
                   library)]
     (->> sources
          (mapcat #(expand-source root-dir %))
          (map str)))))

(defn root-dir
  ^Path [project]
  (::root-dir project))

(defn- update-digest-from-file
  [^MessageDigest digest ^Path f]
  (let [^FileTime last-modified (-> (fs/read-attributes f "lastModifiedTime")
                                    :lastModifiedTime)
        b                       (ByteBuffer/allocate 8)]
    (.putLong b (.toMillis last-modified))
    (.flip b)
    (.update digest b)))

(defn- hex-string [^bytes input]
  (let [sb (StringBuilder. 38)]
    (run! #(.append sb (format "%X" %)) input)
    (str sb)))

(defn project-hash
  "Computes a hash for the project, which is based on the
  timestamps of the dialog.edn file, and on each file identified
  by any of the sources. This is used to quickly check if 
  the in-memory project is out of date and needs to be reloaded.
  
  Adding or deleting files from any source directory will also change
  the hash."
  [project]
  (let [{::keys [root-dir]
         :keys  [sources]} project
        {:keys [main debug library]} sources
        digest    (MessageDigest/getInstance "SHA-1")
        root-path (fs/path root-dir "dialog.edn")
        _         (->> [main debug library]
                       (reduce into [])
                       (map #(fs/path root-dir %))
                       (mapcat #(fs/glob % "*.dg" {:follow-links true}))
                       (into [root-path])
                       sort
                       (run! #(update-digest-from-file digest %)))
        bs        (.digest digest)]
    ;; Byte arrays don't compare as equals, so convert to a hex string
    ;; for later comparison.
    (hex-string bs)))
