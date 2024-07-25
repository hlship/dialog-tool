(ns dialog-tool.skein.process
  (:require [babashka.fs :as fs]
            [clojure.core.async :refer [chan close! >!! <!!]]
            [dialog-tool.project-file :as pf])
  (:import (java.io BufferedReader IOException PrintWriter)
           (java.util List)))


(defn- conj-line
  [lines buffer line-start line-end-inclusive]
  (cond-> lines
          (>= line-end-inclusive line-start) (conj (String/valueOf buffer line-start (- (inc line-end-inclusive) line-start)))))

(defn- split-buffer
  [buffer n-read]
  (loop [i 0
         line-start 0
         lines []]
    (cond
      (> line-start n-read)
      lines

      (>= i n-read)
      (conj-line lines buffer line-start (dec i))

      (= (aget buffer i) \newline)
      (recur (inc i)
             (inc i)
             (conj-line lines buffer line-start i))

      :else
      (recur (inc i)
             line-start
             lines))))

(defn- read-loop
  [^BufferedReader r output-ch]
  ;; Buffer really only needs to be big enough to read a single line
  (let [buffer-size 100
        buffer (char-array buffer-size)
        sb (StringBuilder. 1000)]
    (loop []
      ;; Only read as much as is ready, so that we don't block at a bad time. We need to read
      ;; up to the prompt (which will not have a trailing new line). Reading past the prompt will block.
      (let [n-read (.read r buffer 0 buffer-size)]
        (if (neg? n-read)
          (close! output-ch)
          (let [lines (split-buffer buffer n-read)
                last-line (last lines)]
            ;; TODO: This will cause use pain later because there's a few places where Dialog may
            ;; prompt us before the ">".  Further, Dialog can read a single key, have to figure out
            ;; what that does.  Playing with streams at this low level is painful.  Would a PushbackReader
            ;; make it easier?
            (if (= last-line "> ")
              (do
                ;; Everything but the trailing prompt line goes into
                ;; a single string (which includes newlines) and is conveyed through
                ;; the channel.
                (run! #(.append sb %) (butlast lines))
                (>!! output-ch (.toString sb))
                (.setLength sb 0)
                ;; Add the prompt back in; the *next* command will be echoed after and the transcript will
                ;; look correct e.g., "> go east".
                (.append sb "> ")
                ;; The prompt doesn't go into the output channel
                (recur))
              (do
                ;; This can be zero, especially when the sub-process dies or is killed.
                (when (pos? n-read)
                  (.append sb buffer 0 n-read))
                (recur)))))))))


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