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
  (let [{:keys [pre-flight post-process]} opts
        ;; pre-flight is optional, used to build the .zblorb file for example,
        ;; so it must be before starting the process.
        _ (when pre-flight
            (pre-flight))
        process (-> (PtyProcessBuilder.)
                    (.setCommand (into-array String cmd))
                    (.setInitialColumns (int 80))
                    (.setInitialRows Integer/MAX_VALUE)
                    .start)
        output-ch (chan)]
    (env/debug-command cmd)
    {:process process
     :project project
     :hash (pf/project-hash project)
     :cmd cmd
     :opts opts
     :stdin-writer (-> process .outputWriter PrintWriter.)
     :output-ch output-ch
     :thread (start-thread (.inputReader process) output-ch (or post-process identity))}))

(defn- trim-returns
  [s]
  (string/replace s "\r" ""))

(def ^:private sgr-pattern #"\u001b\[[0-9;]*m")

(def ^:private sgr-reset "\u001b[0m")

(defn- close-ansi
  "Appends an SGR reset sequence to the string if the last ANSI SGR escape
   in the string is not itself a reset. This ensures that when the skein file
   is viewed in a terminal, formatting from one response doesn't bleed into
   the next."
  [s]
  (let [last-reset (string/last-index-of s sgr-reset)
        ;; Check if there are any SGR sequences after the last reset
        has-trailing-sgr? (re-find sgr-pattern
                                   (if last-reset
                                     (subs s (+ last-reset (count sgr-reset)))
                                     s))]
    (if has-trailing-sgr?
      (let [last-nl (string/last-index-of s \newline)]
        (if (and last-nl (= last-nl (dec (count s))))
          ;; Insert reset before the trailing newline
          (str (subs s 0 last-nl) sgr-reset "\n")
          (str s sgr-reset)))
      s)))

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
                  "--numbered"
                  "--seed" (str seed)
                  "--width" "80"]
                 (into (:extra-arguments opts))
                 (into (pf/expand-sources project {:debug? true})))]
     (start! project cmd {:use-pty? true
                          :post-process (fn [s]
                                          (-> s
                                              trim-returns
                                              (string/replace-first #"^\u001b\[0m" "")
                                              close-ansi))}))))

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
  (let [{:keys [^PrintWriter stdin-writer opts]} process
        _ (doto stdin-writer
            (.print command)
            .println
            .flush)]
    (read-response! process)))

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
