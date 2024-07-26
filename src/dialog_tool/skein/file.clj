(ns dialog-tool.skein.file
  "Read or write the skein to a file."
  (:require [babashka.fs :as fs]
            [dialog-tool.skein.tree :as tree]
            [clojure.java.io :as io])
  (:import (java.io BufferedOutputStream FileOutputStream PrintWriter)))

(def ^:private sep
  "--------------------------------------------------------------------------------")
(def ^:private unblessed-sep
  "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")

(defn- p
  [^PrintWriter out ^String label v]
  (doto out
    (.print label)
    (.print ": ")
    (.println (str v))))

(defn write-skein
  [tree ^PrintWriter out]
  (let [{:keys [meta nodes]} tree]
    (p out "seed" (:seed meta))
    (doseq [node-id (-> tree :nodes keys sort)
            :let [{:keys [id parent-id response unblessed command]} (get nodes node-id)]]
      (.println out sep)
      (p out "id" id)
      (when parent-id
        ;; The START node has no parent
        (p out "parent-id" parent-id))
      (when command
        ;; The START node has no command
        (p out "command" command))
      (.println out sep)
      ;; TODO: tags, label, etc. once they exist
      ;; response may not exist if not yet blessed
      (when response
        (.println out ^String response))
      (when unblessed
        (.println out unblessed-sep)
        (.println out unblessed)))))

(defn save-skein
  "Saves the Skein to the file identified by the given path.  Writes the file atomically,
  then returns the tree."
  [tree path]
  (let [path'     (fs/path path)
        temp-path (-> path'
                      fs/canonicalize
                      fs/parent
                      (fs/path (str "_" (fs/file-name path'))))]
    (with-open [out (-> temp-path
                        fs/file
                        io/writer
                        PrintWriter.)]
      (write-skein tree out))

    (fs/move temp-path path' {:replace-existing true
                              :atomic-move      true})

    tree))

(comment

  (let [id1 (tree/next-id)
        id2 (tree/next-id)]
    (-> (tree/new-tree)
        (assoc-in [:meta :seed] 998877)
        (tree/update-response 0 "Wicked Cool Adventure\n")
        (tree/add-child 0 id1 "look" "room description")
        (tree/add-child id1 id2 "get lamp" "You pick up the lamp.\n")
        (tree/bless-node id2)
        (tree/update-response id2 "You pick up the dusty lamp.\n")
        (save-skein "game.skein")))

  )

