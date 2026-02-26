(ns dialog-tool.skein.process
  "Management of a sub-process running the Dialog debugger,
  sending it commands to execute, and receiving back
  the results (output) from those commands.
  
  In addition, can track the files on the project and determine when
  they have changed (thus needing a restart of the process for
  accurate results)."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.core.async :refer [chan close! >!! <!!]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [dialog-tool.env :as env]
            [dialog-tool.project-file :as pf])
  (:import (com.pty4j PtyProcessBuilder)
           (java.io BufferedReader PrintWriter)
           (java.util List)))

(defn- check-for-end-of-response
  [sb output-ch post-process]
  (let [s (.toString sb)]
    (when (string/ends-with? s "> ")
      (let [last-nl (string/last-index-of s \newline)]
        (assert (pos? last-nl))
        (.setLength sb 0)
        ;; Keep only up to (and including) the newline
        (>!! output-ch
             (post-process (subs s 0 (inc last-nl))))
        ;; Put prompt back into the SB
        (.append sb (subs s (inc last-nl)))))))

(defn- read-loop
  [^BufferedReader r output-ch post-process]
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
            (print (String/valueOf buffer 0 n-read))
            (.append sb buffer 0 n-read)
            (check-for-end-of-response sb output-ch post-process)
            (recur)))))))

(defn- start-thread
  [reader output-ch post-process]
  (let [f #(read-loop reader output-ch post-process)]
    (doto (Thread. ^Runnable f "skein process output reader")
      (.setDaemon true)
      .start)))

(defn- start!
  [project ^List cmd opts]
  (let [{:keys [pre-flight post-process use-pty?]} opts
        ;; pre-flight is optional, used to build the .zblorb file for example,
        ;; so it must be before starting the process.
        _         (when pre-flight
                    (pre-flight))
        process   (if use-pty?
                    (-> (PtyProcessBuilder.)
                        (.setCommand (into-array String cmd))
                        (.setInitialColumns (int 80))
                        (.setInitialRows Integer/MAX_VALUE)
                        .start)
                    (-> (ProcessBuilder. cmd)
                        .start))
        output-ch (chan)]
    (env/debug-command cmd)
    {:process      process
     :project      project
     :hash         (pf/project-hash project)
     :cmd          cmd
     :opts         opts
     :stdin-writer (-> process .outputWriter PrintWriter.)
     :output-ch    output-ch
     :thread       (start-thread (.inputReader process) output-ch (or post-process identity))}))

(defn start-debug-process!
  "Starts a Skein process using the Dialog debugger."
  [project-root seed]
  (let [project (pf/read-project project-root)
        cmd (-> [(pf/command-path project "dgdebug")
                 "--quit"
                 "--seed" (str seed)
                 "--width" "80"]
                (into (pf/expand-sources project {:debug? true})))]
    (start! project cmd {:use-pty? true
                         :post-process #(string/replace % "\r" "")})))

(def engines #{:dgdebug :frotz :frotz-release})

(defmulti start-process! (fn [_project-root engine _seed] engine))

(defmethod start-process! :dgdebug
  [project-root _ seed]
  (start-debug-process! project-root seed))

(def ^:private *patch-path
  (delay
    (let [file "dfrotz-skein-patch.dg"
          dir (fs/create-temp-dir)
          path (fs/path dir file)]
      (-> (io/resource file)
          io/input-stream
          (io/copy (fs/file path)))
      path)))

(defn- start-frotz-process
  [project-root seed debug?]
  (let [project (pf/read-project project-root)
        {project-name :name} project
        project-dir (pf/root-dir project)
        output-dir (fs/path project-dir "out" "skein" (if debug? "debug" "release"))
        path (fs/path output-dir (str project-name ".zblorb"))
        pre (fn []
              (let [command (into ["dialogc"
                                   "--format" "zblorb"
                                   "--output" (str path)]
                                  ;; the patch prevents the status line from being presented
                                  ;; (otherwise it shows up inline)
                                  (pf/expand-sources project {:debug? true
                                                              :pre-patch [@*patch-path]}))]
                (env/debug-command command)
                (p/check
                 (p/sh command))))
        cmd ["dfrotz"
             ;; Flags: quiet, no *more*
             "-q" "-m"
             "-s" (str seed)
             "-w" "80"
             (str path)]]
    (fs/create-dirs output-dir)
    (start! project
            cmd
            {:pre-flight pre
             :echo-command true})))

(defmethod start-process! :frotz
  [project-root _ seed]
  (start-frotz-process project-root seed true))

(defmethod start-process! :frotz-release
  [project-root _ seed]
  (start-frotz-process project-root seed false))

(defn read-response!
  [process]
  (-> process :output-ch <!!))

(defn- inject-command
  [command response]
  (str
   (subs response 0 2)
   command
   "\n"
   (subs response 2)))

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
    (when process
      (.destroy process)))
  nil)

(defn sources-changed?
  "Checks to see if the project (dialog.edn, or any .dg file) has changed
  since this process was created."
  [process]
  (let [{:keys [project hash]} process]
    (not= hash (pf/project-hash project))))
