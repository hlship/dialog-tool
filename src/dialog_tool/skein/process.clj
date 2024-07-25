(ns dialog-tool.skein.process
  (:require [babashka.fs :as fs]
            [clojure.core.async :refer [chan close! >!! <!!]]
            [clojure.string :as string]
            [dialog-tool.project-file :as pf])
  (:import (java.io BufferedReader PrintWriter)
           (java.util List)))


(defn- check-for-end-of-response
  [sb output-ch]
  (let [s (.toString sb)]
    (when (string/ends-with? s "> ")
      (let [last-nl (string/last-index-of s \newline)]
        (assert (pos? last-nl))
        (.setLength sb 0)
        ;; Send everything upto and including the newline
        (>!! output-ch (subs s 0 (inc last-nl)))
        ;; Put prompt back into the SB
        (.append sb (subs s (inc last-nl)))))))

(defn- read-loop
  [^BufferedReader r output-ch]
  ;; TODO: Verify this all works as expected when we need to read into the buffer multiple times.
  (let [buffer-size 2000
        buffer (char-array buffer-size)
        sb (StringBuilder. buffer-size)]
    (loop []
      ;; Only read as much as is ready, so that we don't block at a bad time. We need to read
      ;; up to the prompt (which will not have a trailing new line). Reading past the prompt will block.
      (let [n-read (.read r buffer 0 buffer-size)]
        (if (neg? n-read)
          (close! output-ch)
          (do
            (.append sb buffer 0 n-read)
            (check-for-end-of-response sb output-ch)
            (recur)))))))

(defn- start-thread
  [reader output-ch]
  (let [f #(read-loop reader output-ch)
        thread (doto (Thread. ^Runnable f "dialog debug reader")
                 (.setDaemon true)
                 .start)]
    thread))

(defn start-debug-process
  [debugger-path project]
  (let [^List cmd (-> [debugger-path "--quit" "--width" "80"]
                      (into (pf/expand-sources project {:debug? true})))
        dir (-> project :dir fs/file)
        process (-> (ProcessBuilder. cmd)
                    (.directory dir)
                    .start)
        stdout-reader (.inputReader process)
        output-ch (chan 20)]
    {:process       process
     :dir           dir
     :cmd           cmd
     :stdout-reader stdout-reader                           ; read stdout of process
     :stdin-writer  (-> process .outputWriter PrintWriter.) ; write stdin of process
     :output-ch     output-ch
     :thread        (start-thread stdout-reader output-ch)}))

(defn read-response!
  [debug-process]
  (-> debug-process :output-ch <!!))

(defn write-command!
  [debug-process ^String cmd]
  (let [{:keys [^PrintWriter stdin-writer]} debug-process]
    (doto stdin-writer
      (.print cmd)
      .println
      .flush))
  (read-response! debug-process))

(defn kill!
  [debug-process]
  (let [{:keys [^Process process]} debug-process]
    (.destroy process)))