(ns dialog-tool.skein.file
  "Read or write the skein to a file."
  (:require [babashka.fs :as fs]
            [dialog-tool.skein.tree :as tree]
            [clojure.java.io :as io])
  (:import (java.io IOException LineNumberReader PrintWriter)))

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
       (re-matches #"<+{4,}" s)))

(defn- s->long
  [s]
  (or (parse-long s)
      (throw (IllegalArgumentException. (format "Could not parse '%s' as a number" s)))))

(defn write-skein
  [tree ^PrintWriter out]
  (let [{:keys [meta nodes]} tree]
    (p out "seed" (:seed meta))
    (doseq [node-id (-> tree :nodes keys sort)
            :let [{:keys [id parent-id response unblessed command label]} (get nodes node-id)]]
      (.println out sep)
      (p out "id" id)
      (p out "label" label)
      (when parent-id
        ;; The START node has no parent
        (p out "parent-id" parent-id))
      (when command
        ;; The START node has no command
        (p out "command" command))
      (.println out sep)
      ;; TODO: tags, label, etc. once they exist
      ;; response may not exist if not yet blessed
      ;; Also, these strings end with a newline
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
  [^LineNumberReader r]
  (loop [meta {}]
    (let [line (.readLine r)]
      (if (sep? line)
        {:meta meta}
        (let [[k v] (parse-kv line)]
          (if k
            (recur (apply-kv meta k v meta-parsers))
            (recur meta)))))))

(def node-parsers
  {"id"        s->long
   "parent-id" s->long
   "command"   identity
   "label"     identity})

(defn- apply-content
  [node k ^StringBuilder sb]
  (if (pos? (.length sb))
    (let [s (str sb)]
      (.setLength sb 0)
      (assoc node k s))
    node))

(defn- read-content
  [initial-node ^LineNumberReader r]
  (let [sb (StringBuilder. 1000)]
    (loop [node initial-node
           k :response]
      (let [line (.readLine r)]
        (cond
          (sep? line)
          (apply-content node k sb)

          (unblessed-sep? line)
          (recur (apply-content node k sb) :unblessed)

          :else
          (do
            (.append sb ^String line)
            (.append sb \newline)
            (recur node k)))))))

(defn- read-node
  [^LineNumberReader r]
  (loop [node nil]
    (let [line (.readLine r)]
      (cond
        (nil? line)
        node

        (= sep line)
        (read-content node r)

        :else
        (let [[k v] (parse-kv line)]
          (if k
            (recur (apply-kv node k v node-parsers))
            (recur node)))))))

(defn- read-nodes
  [initial-tree ^LineNumberReader in]
  (loop [tree initial-tree]
    (if-let [node (read-node in)]
      ;; Just add the node, we rebuild the :children key for each node at the end
      (recur (assoc-in tree [:nodes (:id node)] node))
      tree)))

(defn read-skein
  [^LineNumberReader in]
  (try
    (-> (read-meta in)
        (read-nodes in)
        tree/rebuild-children)
    (catch Exception e
      (let [m (or (ex-message e)
                  (-> e class .getName))
            line (.getLineNumber in)]
        (throw (IOException. (format "Unable to read skein: %s (on line %d)"
                                     m line)
                             e))))))

(defn load-skein
  [path]
  (with-open [reader (-> path
                         fs/path
                         fs/file
                         io/reader
                         LineNumberReader.)]
    (read-skein reader)))

(defn save-skein
  "Saves the Skein to the file identified by the given path.  Writes the file atomically,
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
