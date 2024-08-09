(ns dialog-tool.skein.process
  "Management of a sub-process running the Dialog debugger,
  sending it commands to execute, and receiving back
  the results (output) from those commands."
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
        ;; Keep only up to (and including) the newline
        (>!! output-ch
             (subs s 0 (inc last-nl)))
        ;; Put prompt back into the SB
        (.append sb (subs s (inc last-nl)))))))

(defn- read-loop
  [^BufferedReader r output-ch]
  (let [buffer-size 5000
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
  (let [f #(read-loop reader output-ch)]
    (doto (Thread. ^Runnable f "dialog debugger reader")
      (.setDaemon true)
      .start)))

(defn- start!
  [dir ^List cmd seed]
  (let [process (-> (ProcessBuilder. cmd)
                    (.directory dir)
                    .start)
        stdout-reader (.inputReader process)
        output-ch (chan)]
    {:process      process
     :seed         seed
     :dir          dir
     :cmd          cmd
     :stdin-writer (-> process .outputWriter PrintWriter.)  ; write stdin of process
     :output-ch    output-ch
     :thread       (start-thread stdout-reader output-ch)}))

(defn start-debug-process!
  "Starts a Skein process using the Dialog debugger."
  [project seed]
  (let [^List cmd (-> ["dgdebug"
                       "--quit"
                       "--seed" (str seed)
                       "--width" "80"]
                      (into (pf/expand-sources project {:debug? true})))
        dir (-> project :dir fs/file)]
    (start! dir cmd seed)))

(defn read-response!
  [debug-process]
  (-> debug-process :output-ch <!!))

(defn send-command!
  "Sends a player command to the process, blocking until a response to the command
  is available.  Returns the response."
  [debug-process ^String cmd]
  (let [{:keys [^PrintWriter stdin-writer]} debug-process]
    (doto stdin-writer
      (.print cmd)
      .println
      .flush))
  (read-response! debug-process))

(defn kill!
  "Kills the debug process and returns nil."
  [debug-process]
  (let [{:keys [^Process process]} debug-process]
    (.destroy process))
  nil)

(defn restart!
  "Kills the debug process and starts a new one, returning a new process map."
  [debug-process]
  (kill! debug-process)
  (let [{:keys [dir cmd seed]} debug-process]
    (start! dir cmd seed)))
