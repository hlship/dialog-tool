(ns dialog-tool.skein.process
  "Management of a sub-process running the Dialog debugger,
  sending it commands to execute, and receiving back
  the results (output) from those commands."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.core.async :refer [chan close! >!! <!!]]
            [clojure.string :as string]
            [dialog-tool.project-file :as pf]
            [dialog-tool.util :as util])
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
    (doto (Thread. ^Runnable f "skein process output reader")
      (.setDaemon true)
      .start)))

(defn- start!
  [^List cmd opts]
  (let [{:keys [pre-flight]} opts
        ;; pre-flight is optional, used to build the .zblorb file for example,
        ;; so it must be before starting the process.
        _ (when pre-flight
            (pre-flight))
        process (-> (ProcessBuilder. cmd)
                    .start)
        stdout-reader (.inputReader process)
        output-ch (chan)]
    {:process      process
     :cmd          cmd
     :opts         opts
     :stdin-writer (-> process .outputWriter PrintWriter.)  ; write stdin of process
     :output-ch    output-ch
     :thread       (start-thread stdout-reader output-ch)}))

(defn start-debug-process!
  "Starts a Skein process using the Dialog debugger."
  [project seed]
  (let [cmd (-> ["dgdebug"
                 "--quit"
                 "--seed" (str seed)
                 "--width" "80"]
                (into (pf/expand-sources project {:debug? true})))]
    (start! cmd nil)))

(def engines #{:dgdebug :frotz :frotz-release})

(defmulti start-process! (fn [_project engine _seed] engine))

(defmethod start-process! :dgdebug
  [project _ seed]
  (start-debug-process! project seed))


(defn- start-frotz-process
  [project seed debug?]
  (let [{project-name :name} project
        project-dir (pf/project-dir project)
        output-dir (fs/path project-dir "out" "skein" (if debug? "debug" "release"))
        path (fs/path output-dir (str project-name ".zblorb"))
        pre (fn []
              (p/check
                (p/sh (into ["dialogc"
                             "--format" "zblorb"
                             "--output" (str path)]
                            ;; the dumb-patch? option prevents the status bar from being output at all
                            ;; (otherwise it shows up inline)
                            (pf/expand-sources project {:debug?    true
                                                        :pre-patch [(util/root-path "resources" "dfrotz-skein-patch.dg")]})))))
        cmd ["dfrotz"
             ;; Flags: quiet, no *more*
             "-q" "-m"
             "-s" (str seed)
             "-w" "80"
             (str path)]]
    (fs/create-dirs output-dir)
    (start! cmd {:pre-flight   pre
                 :echo-command true})))

(defmethod start-process! :frotz
  [project _ seed]
  (start-frotz-process project seed true))

(defmethod start-process! :frotz-release
  [project _ seed]
  (start-frotz-process project seed false))

(defn read-response!
  [process]
  (-> process :output-ch <!!))

(defn- inject-command
  [command response]
  (let [x (string/index-of response "\n")]
    (str
      (subs response 0 x)
      command
      (subs response (inc x)))))

(defn send-command!
  "Sends a player command to the process, blocking until a response to the command
  is available.  Returns the response."
  [process ^String command]
  (let [{:keys [^PrintWriter stdin-writer opts]} process
        {:keys [echo-command]} opts
        _ (doto stdin-writer
            (.print command)
            .println
            .flush)
        raw-response (read-response! process)]
    ;; dgdebug echos back what it receives on its stdin, but dfrotz does not.
    ;; The echo-command flag lets us fake it.
    (if echo-command
      (inject-command command raw-response)
      raw-response)))

(defn kill!
  "Kills the process and returns nil."
  [process]
  (let [{:keys [^Process process]} process]
    (.destroy process))
  nil)

(defn restart!
  "Kills the process and starts a new one, returning a new process map."
  [process]
  (kill! process)
  (let [{:keys [cmd opts]} process]
    (start! cmd opts)))
