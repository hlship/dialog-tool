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
  (:import (com.pty4j.util PtyUtil)
           (java.io BufferedReader InputStreamReader OutputStreamWriter PrintWriter)
           (java.util ArrayList List)))
(defn alive?
  "Given a process map, returns true if the underlying OS Process is still alive."
  [process]
  (let [^Process p (:process process)]
    (.isAlive p)))

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
  (let [buffer-size 100000
        buffer (char-array buffer-size)
        sb (StringBuilder. buffer-size)]
    (loop []
      ;; Only read as much as is ready, so that we don't block at a bad time. We need to read
      ;; up to the prompt (which will not have a trailing new line). Reading past the prompt will block.
      (let [n-read (try
                     (.read r buffer 0 buffer-size)
                     (catch java.io.IOException _
                       ;; PTY closed or bad fd after process kill — treat as EOF
                       -1))]
        (if (neg? n-read)
          ;; Process died, pipe closed -- this can happen when there are errors in
          ;; the source code.  Provide as much as was read:
          (do
            (>!! output-ch (-> sb str post-process string/trim))
            (close! output-ch))
          (do
            (.append sb buffer 0 n-read)
            (check-for-end-of-response sb output-ch post-process)
            (recur)))))))

(defn- start-thread
  [reader output-ch post-process]
  (let [f #(read-loop reader output-ch post-process)]
    (doto (Thread. ^Runnable f "skein process output reader")
      (.setDaemon true)
      .start)))

(defn- open-pty-process!
  "Creates a PTY pair and spawns cmd via pty4j's native spawn-helper binary,
  bypassing exec_pty (which crashes with SIGBUS on Apple Silicon).

  The spawn helper opens the slave PTY device, redirects stdin/stdout/stderr to
  it, then exec's the actual command — so the child sees a real TTY and outputs
  ANSI escape codes normally. Because exec() replaces the helper's process image,
  the resulting Process handle points directly at the command (correct for
  alive? and destroyForcibly).

  Equivalent to what pty4j's ProcessBuilderUnixLauncher does on Mac Intel,
  extended to all Unix platforms."
  [cmd]
  (let [pty-class  (Class/forName "com.pty4j.unix.Pty")
        pty-ctor   (.getConstructor pty-class (into-array Class []))
        pty        (try
                     (.newInstance pty-ctor (into-array Object []))
                     (catch java.lang.reflect.InvocationTargetException e
                       (throw (or (.getCause e) e))))
        slave-name (.getSlaveName pty)
        master-fd  (.getMasterFD pty)]
    (when (or (< master-fd 0) (= slave-name "/dev/ptmx"))
      (try (.close pty) (catch Exception _))
      (throw (java.io.IOException.
              (str "PTY creation failed (posix_openpt returned error); "
                   "a system reboot may be required to recover the PTY subsystem. "
                   "master-fd=" master-fd))))
    (let [helper     (PtyUtil/resolveNativeFile "pty4j-unix-spawn-helper")
          helper-cmd (into [(str helper)
                            "."         ; working directory (inherit cwd)
                            "0"         ; console mode = false
                            slave-name
                            (str master-fd)
                            ""          ; no error-pty slave name
                            "-1"]       ; no error-pty master fd
                           cmd)
          devnull    (java.io.File. "/dev/null")
          process    (-> (ProcessBuilder. ^List (ArrayList. ^List helper-cmd))
                         (.redirectInput devnull)
                         (.redirectOutput devnull)
                         (.redirectErrorStream true)
                         .start)]
      {:process      process
       :pty          pty
       :stdin-writer (-> pty .getOutputStream OutputStreamWriter. PrintWriter.)
       :input-reader (-> pty .getInputStream InputStreamReader. BufferedReader.)})))

(defn- start!
  [project ^List cmd opts]
  (let [{:keys [pre-flight post-process]} opts
        ;; pre-flight is optional, used to build the .zblorb file for example,
        ;; so it must be before starting the process.
        _ (when pre-flight
            (pre-flight))
        {:keys [^Process process pty stdin-writer input-reader]} (open-pty-process! cmd)
        output-ch (chan)]
    (env/debug-command cmd)
    {:process process
     :pty pty
     :project project
     :hash (pf/project-hash project)
     :cmd cmd
     :opts opts
     :stdin-writer stdin-writer
     :input-reader input-reader
     :output-ch output-ch
     :thread (start-thread input-reader output-ch (or post-process identity))}))

(defn- trim-returns
  [s]
  (string/replace s "\r" ""))

(defn start-debug-process!
  "Starts a Skein process using the Dialog debugger.
   opts is an optional map; :extra-arguments are added to the command line
   before the source files."
  ([project-root seed]
   (start-debug-process! project-root seed nil))
  ([project-root seed opts]
   (let [project (pf/read-project project-root)
         cmd (-> [(pf/command-path project "dgdebug")
                  "--quit"
                  "--no-header"
                  "--numbered"
                  "--seed" (str seed)
                  "--width" "-1"
                  "--height" "-1"]
                 (into (:extra-arguments opts))
                 (into (pf/expand-sources project {:debug? true})))]
     (start! project cmd {:use-pty? true
                          :post-process (fn [s]
                                          (-> s
                                              trim-returns
                                              ;; TODO: Is this still needed?
                                              (string/replace-first #"^\u001b\[0m" "")))}))))

(def engines #{:dgdebug :frotz :frotz-release})

(defmulti start-process!
  "Starts a game process. opts is a map; :extra-arguments are
   additional command-line arguments passed to the engine."
  (fn [_project-root engine _seed _opts] engine))

(defmethod start-process! :dgdebug
  [project-root _ seed opts]
  (start-debug-process! project-root seed opts))

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
  [project-root seed opts]
  (let [project (pf/read-project project-root)
        {project-name :name} project
        {:keys [debug?]} opts
        project-dir (pf/root-dir project)
        output-dir (fs/path project-dir "out" "skein" (if debug? "debug" "release"))
        path (fs/path output-dir (str project-name ".zblorb"))
        pre (fn []
              (let [command (into [(pf/command-path project "dialogc")
                                   "--format" "zblorb"
                                   "--output" (str path)]
                                  ;; the patch prevents the status line from being presented
                                  ;; (otherwise it shows up inline)
                                  (pf/expand-sources project {:debug? debug?
                                                              :pre-patch [@*patch-path]}))]
                (env/debug-command command)
                (p/check
                 (p/sh command))))
        cmd [(pf/command-path project "dfrotz")
             ;; Flags: quiet, no *more*
             "-q" "-m"
             ;; Although dfortz has "-f ansi", when enabled
             ;; it is closer to a ncurses kind of output,
             ;; with right padding of lines, and ANSI 
             ;; Erase in Line (CSI K) sequences.
             ;; So, validate your formatting using dgdebug,
             ;; and validate your zcode with dfrotz.
             "-s" (str seed)
             "-w" "80"
             (str path)]]
    (fs/create-dirs output-dir)
    (start! project
            cmd
            {:pre-flight pre
             :post-process trim-returns})))

(defmethod start-process! :frotz
  [project-root _ seed opts]
  (start-frotz-process project-root seed (assoc opts :debug? true)))

(defmethod start-process! :frotz-release
  [project-root _ seed opts]
  (start-frotz-process project-root seed (assoc opts :debug? false)))

(defn read-response!
  [process]
  (-> process :output-ch <!!))

(defn send-command!
  "Sends a player command to the process, blocking until a response to the command
  is available.  Returns the response."
  [process ^String command]
  (let [{:keys [^PrintWriter stdin-writer]} process
        _ (doto stdin-writer
            (.println command)
            .flush)]
    (read-response! process)))

(defn kill!
  "Kills the OS process and returns nil."
  [process]
  (let [{:keys [^Process process pty output-ch]} process]
    (when process
      (.destroyForcibly process))
    ;; Explicitly close the Pty so the reader thread sees EOF and the master fd
    ;; is released promptly. Safe to do immediately because we spawn via
    ;; ProcessBuilder (no exec_pty), so fd reuse is not a concern.
    (when pty
      (try (.close pty) (catch Exception _)))
    (when output-ch
      (close! output-ch)))
  nil)

(defn sources-changed?
  "Checks to see if the project (dialog.edn, or any .dg file) has changed
  since this process was created."
  [process]
  (let [{:keys [project hash]} process]
    ;; If the project has not yet been loaded (i.e., immediately
    ;; after startup and before the automatic Replay All has occurred)
    ;; then assume a change to force the initial process to be launched.
    (or (nil? project)
        (not= hash (pf/project-hash project)))))
