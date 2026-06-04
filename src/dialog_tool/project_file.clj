(ns dialog-tool.project-file
  (:require [clojure.edn :as edn]
            [net.lewisship.cli-tools :refer [abort]]
            [babashka.fs :as fs])
  (:import (java.nio ByteBuffer)
           (java.nio.file Path)
           (java.nio.file.attribute FileTime)
           (java.security MessageDigest)))

(defn- normalize-target
  "Normalizes the :target key to always be a vector of keywords.
  A single keyword (e.g., :zblorb) becomes [:zblorb]; a sequence stays as-is.
  Also supports the legacy :format key (renamed to :target).
  Defaults to [:zblorb] when neither :target nor :format is specified."
  [project]
  (let [target (or (:target project) (:format project))]
    (cond-> (dissoc project :format)
      (keyword? target) (assoc :target [target])
      (sequential? target) (assoc :target (vec target))
      (nil? target) (assoc :target [:zblorb]))))

(defn read-project
  ;; 0 arity is normal, 1 arity is just for testing purposes
  ([]
   (read-project ""))
  ([root-dir]
   (let [root-dir' (fs/path (or root-dir ""))
         path      (fs/path root-dir' "dialog.edn")]
     (when-not (fs/exists? path)
       (abort (str path) " does not exist"))
     (try
       (-> path
           fs/file
           slurp
           edn/read-string
           normalize-target
           (assoc ::root-dir root-dir'))
       (catch Throwable t
         (abort "Could not read " [:bold path] ": "
                (ex-message t)))))))

(defn- expand-source
  [root-dir source]
  (if (instance? Path source)
    [source]
    (let [path (fs/path root-dir source)]
      (cond

        (fs/directory? path)
        (sort (fs/glob (fs/path root-dir source) "*.dg" {:follow-links true}))

        (fs/exists? path)
        [path]

        :else
        nil))))

(defn- filter-by-target
  [target source-paths]
  (if-not target
    source-paths
    (let [target' (name target)
          f       (fn [path]
                    (let [file-name (fs/file-name path)
                          [_ file-target] (re-matches #"(?ix)
                          .+  # start of filename
                          \.
                          (.+) # target embedded in filename
                          \Q.dg\E$"
                                                      file-name)]
                      (or (nil? file-target)
                          (= target' file-target))))]
      (filter f source-paths))))

(defn expand-sources
  "Expands the sources for the project.
  
  Order:
  * pre-patch
  * :main
  * :test
  * :debug
  * :library
  
  Options:
  
  * debug? - include :debug sources
  * test?  - include :test sources
  * target - if non-nil, remove sources for other targets.
  * pre-patch - seq of paths that are injected prior to :main sources
  
  File names may encode a target, i.e., `effects.zblorb.dg`; the target would
  be `zblorb` and the file will be excluded if its target does not match the provided
  target.  Most files do not have a target and are always included."
  ([project]
   (expand-sources project nil))
  ([project {:keys [debug? test? pre-patch target]}]
   (let [{::keys [root-dir]
          :keys  [sources]} project
         {:keys [main test debug library]} sources
         sources (concat
                   pre-patch
                   main
                   (when test? test)
                   (when debug? debug)
                   library)]
     (->> (mapcat #(expand-source root-dir %) sources)
          (filter-by-target target)
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
  the hash.
  
  This doesn't consider target, so it may yield a false positive
  (a change to the hash) even when a potential file, excluded due to 
  target, is modified."
  [project]
  (let [{::keys [root-dir]
         :keys  [sources]} project
        {:keys [main test debug library]} sources
        digest    (MessageDigest/getInstance "SHA-1")
        root-path (fs/path root-dir "dialog.edn")
        _         (->> [main test debug library]
                       (reduce into [root-path])
                       (mapcat #(expand-source root-dir %))
                       sort
                       (run! #(update-digest-from-file digest %)))
        bs        (.digest digest)]
    ;; Byte arrays don't compare as equals, so convert to a hex string
    ;; for later comparison.
    (hex-string bs)))

(def ^:private windows?
  (-> (System/getProperty "os.name")
      (.toLowerCase)
      (.contains "win")))

(defn command-path
  [project command]
  (let [{:keys [bin-dir]} project
        command' (if windows?
                   (str command ".exe")
                   command)]
    (if bin-dir
      (str bin-dir "/" command')
      command')))
