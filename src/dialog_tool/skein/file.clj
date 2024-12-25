(ns dialog-tool.skein.file
  "Read or write the skein to a file."
  (:require [babashka.fs :as fs]
            [dialog-tool.skein.tree :as tree]
            [clojure.java.io :as io])
  (:import (java.io FileNotFoundException IOException PrintWriter)
           (clojure.lang LineNumberingPushbackReader)))

(def ^:private sep
  "--------------------------------------------------------------------------------")
(def ^:private unblessed-sep
  "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")

(defn- p
  [^PrintWriter out ^String label v]
  (when v
    (doto out
      (.print label)
      (.print ": ")
      (.println (str v)))))

(defn- sep?
  [s]
  (or (nil? s)
      (re-matches #"-{4,}" s)))

(defn- unblessed-sep?
  [s]
  (and (some? s)
       (re-matches #"<{4,}" s)))

(defn- s->long
  [s]
  (or (parse-long s)
      (throw (IllegalArgumentException. (format "Could not parse '%s' as a number" s)))))

(defn write-skein
  [tree ^PrintWriter out]
  (let [{:keys [meta knots]} tree]
    (p out "seed" (:seed meta))
    (doseq [knot-id (-> tree :knots keys sort)
            ;; Purposely don't write the :selected property as that is only meaningful
            ;; during editing and would cause many unwanted changes to the skein stored in
            ;; version control.  We want skein changes to be reviewable.
            :let [{:keys [id parent-id response unblessed command label]} (get knots knot-id)]]
      (.println out sep)
      (p out "id" id)
      (p out "label" label)
      (p out "parent-id" parent-id)
      (p out "command" command)
      (.println out sep)
      (when response
        (.print out ^String response))
      (when unblessed
        (.println out unblessed-sep)
        (.print out unblessed)))))


(def meta-parsers
  {"seed" s->long})

(def kv-re #"(?x)
    (.+):   # key portion
    \s*     # zero or more spaces
    (.+)    # value (may include trailing spaces)
    ")

(defn- parse-kv
  [line]
  (when-let [[_ k v] (re-matches kv-re line)]
    [k v]))

(defn- apply-kv
  [m k v parsers]
  (if-let [parser (get parsers k)]
    (assoc m (keyword k) (parser v))
    m))

(defn- read-meta
  [^LineNumberingPushbackReader r]
  (loop [meta {}]
    (let [line (.readLine r)]
      (if (sep? line)
        {:meta meta}
        (let [[k v] (parse-kv line)]
          (if k
            (recur (apply-kv meta k v meta-parsers))
            (recur meta)))))))

(def knot-parsers
  {"id"        s->long
   "parent-id" s->long
   "command"   identity
   "label"     identity})

(defn- apply-content
  [knot k ^StringBuilder sb]
  (if (pos? (.length sb))
    (let [s (str sb)]
      (.setLength sb 0)
      (assoc knot k s))
    knot))

(defn- read-content
  [initial-knot ^LineNumberingPushbackReader r]
  (let [sb (StringBuilder. 1000)]
    (loop [knot initial-knot
           k :response]
      (let [line (.readLine r)]
        (cond
          (sep? line)
          (apply-content knot k sb)

          (unblessed-sep? line)
          (recur (apply-content knot k sb) :unblessed)

          :else
          (do
            (.append sb ^String line)
            (.append sb \newline)
            (recur knot k)))))))

(defn- read-knot
  [^LineNumberingPushbackReader r]
  (loop [knot nil]
    (let [line (.readLine r)]
      (cond
        (nil? line)
        knot

        (= sep line)
        (read-content knot r)

        :else
        (let [[k v] (parse-kv line)]
          (if k
            (recur (apply-kv knot k v knot-parsers))
            (recur knot)))))))

(defn- read-knots
  [initial-tree ^LineNumberingPushbackReader in]
  (loop [tree initial-tree]
    (if-let [knot (read-knot in)]
      ;; Just add the knot, we rebuild the :children key for each knot at the end
      (recur (assoc-in tree [:knots (:id knot)] knot))
      tree)))

(defn read-tree
  [^LineNumberingPushbackReader in]
  (try
    (-> (read-meta in)
        (read-knots in)
        tree/rebuild-children)
    (catch Exception e
      (let [m (or (ex-message e)
                  (-> e class .getName))
            line (.getLineNumber in)]
        (throw (IOException. (format "Unable to read skein: %s (on line %d)"
                                     m line)
                             e))))))

(defn load-tree
  [path]
  (when-not (fs/exists? path)
    (throw (FileNotFoundException. (str "No such file: " path))))
  (with-open [reader (-> path
                         fs/path
                         fs/file
                         io/reader
                         LineNumberingPushbackReader.)]
    (read-tree reader)))

(defn save-tree
  "Saves the Skein tree to the file identified by the given path.  Writes the file atomically,
  then returns the tree."
  [tree path]
  (let [path'     (fs/path path)
        temp-path (-> path'
                      fs/canonicalize
                      fs/parent
                      (fs/path (str "_" (fs/file-name path'))))
        parent (fs/parent temp-path)]
    (when-not (fs/exists? parent)
      (fs/create-dir parent))
    (with-open [out (-> temp-path
                        fs/file
                        io/writer
                        PrintWriter.)]
      (write-skein tree out))
    (fs/move temp-path path' {:replace-existing true
                              :atomic-move      true})

    tree))
