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
  (:import (java.io BufferedReader IOException PrintWriter)
           (java.util List)))

(defn alive?
  "Given a process map, returns true if the underlying OS Process is still alive."
  [process]
  (let [^Process p (:process process)]
    (.isAlive p)))

(defn- check-for-end-of-response
  [sb output-ch post-process]
  ;; TODO: Check for single char prompt as well.
  (let [line-input? (string/ends-with? sb "\n> ")
        keystroke?   (string/ends-with? sb "\n) ")]
    (when (or line-input?
              keystroke?)
      (>!! output-ch
           (post-process (str sb)))
      (.setLength sb 0)
      (.append sb "  ")
      ;; Put prompt back into the SB
      (when line-input?
        (.append sb "> ")))))

(defn- read-loop
  [^BufferedReader r output-ch post-process]
  (let [buffer-size    100000
        ^char/1 buffer (char-array buffer-size)
        sb             (StringBuilder. buffer-size)]
    (loop []
      ;; Only read as much as is ready, so that we don't block at a bad time. We need to read
      ;; up to the prompt (which will not have a trailing new line). Reading past the prompt will block.
      (let [n-read (try
                     (.read r buffer 0 buffer-size)
                     (catch IOException _
                       ;; Process died
                       -1))]
        (if (neg? n-read)
          ;; Process died, pipe closed -- this can happen when there are errors in
          ;; the source code.  Provide as much as was read:
          (do
            ;; TODO: Test failures, may need to adjust post process for startup failures
            (>!! output-ch (-> sb str post-process))
            (close! output-ch))
          (do
            (.append sb buffer 0 (int n-read))
            ;; Generally, dgdebug (or frotz) is blazing fast, and so once we get any input
            ;; we get a very complete response, but maybe someone's writing a novel
            ;; and 10K isn't big enough to get it in a single gulp.
            (check-for-end-of-response sb output-ch post-process)
            (recur)))))))

(defn- start-thread
  [reader output-ch post-process]
  (let [f #(read-loop reader output-ch post-process)]
    (doto (Thread. ^Runnable f "skein process output reader")
      (.setDaemon true)
      .start)))

(defn- start!
  [^List cmd opts]
  (let [{:keys [pre-flight post-process]
         :or   {post-process identity}} opts
        ;; pre-flight is optional, used to build the .zblorb file for example,
        ;; so it must be before starting the process.
        _             (when pre-flight
                        (pre-flight))
        process       (-> (ProcessBuilder. cmd)
                          (.redirectErrorStream true)
                          .start)
        stdout-reader (.inputReader process)
        output-ch     (chan)]
    (env/debug-command cmd)
    {:process      process
     :cmd          cmd
     :opts         opts
     :stdin-writer (-> process .outputWriter PrintWriter.)  ; write stdin of process
     :output-ch    output-ch
     :thread       (start-thread stdout-reader output-ch post-process)}))

(defn- trim-returns
  [s]
  (string/replace s "\r" ""))

(defn- remove-tail
  [value lines]
  (if (= value (last lines))
    (recur value (butlast lines))
    lines))

(defn- remove-dgdbebug-taglines
  [response]
  (let [raw-lines    (string/split-lines response)
        lines        (->> raw-lines
                          (drop-while #(= % ""))            ; Initial response can start with empty string for first line
                          butlast                           ; The line with the prompt
                          ;; Most output line start with two spaces
                          ;; The final output line is '>' or ')' and a space
                          (mapv #(subs % 2))
                          (remove-tail "> "))
        single-char? (-> raw-lines
                         last
                         (= ") "))
        response'    (string/replace-first (->> lines
                                                (string/join "\n"))
                                           #"^\u001b\[0m" "")]
    {:response (cond-> response'
                 (not (string/ends-with? response' "/n")) (str "\n"))
     :prompt   (if single-char? :keystroke :line)}))

(defn start-debug-process!
  "Starts a Skein process using the Dialog debugger.
   opts is an optional map; :extra-arguments are added to the command line
   before the source files."
  ([project-root seed]
   (start-debug-process! project-root seed nil))
  ([project-root seed opts]
   (let [project (pf/read-project project-root)
         cmd     (-> [(pf/command-path project "dgdebug")
                      "--numbered"                          ;; Number indentation on trace output
                      "--seed" (str seed)
                      "--width" "-1"                        ;; No set width; UI will word-wrap
                      "--unit-test"
                      "--transcripting"
                      "--tag-lines"
                      "--formatting" "ansi"]
                     (into (:extra-arguments opts))
                     (into (pf/expand-sources project {:debug? true
                                                       :target :dgdebug})))]
     (start! cmd {:post-process #(-> %
                                     trim-returns
                                     remove-dgdbebug-taglines)}))))

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
          dir  (fs/create-temp-dir)
          path (fs/path dir file)]
      (-> (io/resource file)
          io/input-stream
          (io/copy (fs/file path)))
      path)))

(defn- start-frotz-process
  [project-root seed opts]
  (let [project     (pf/read-project project-root)
        {project-name :name} project
        {:keys [debug?]} opts
        project-dir (pf/root-dir project)
        output-dir  (fs/path project-dir "out" "skein" (if debug? "debug" "release"))
        path        (fs/path output-dir (str project-name ".zblorb"))
        pre         (fn []
                      (let [command (into [(pf/command-path project "dialogc")
                                           "--format" "zblorb"
                                           "--output" (str path)]
                                          ;; the patch prevents the status line from being presented
                                          ;; (otherwise it shows up inline)
                                          (pf/expand-sources project {:debug?    debug?
                                                                      :target    :zblorb
                                                                      :pre-patch [@*patch-path]}))]
                        (env/debug-command command)
                        (p/check
                          (p/sh command))))
        cmd         [(pf/command-path project "dfrotz")
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
    (start! cmd
            {:pre-flight   pre
             :post-process trim-returns})))

(defmethod start-process! :frotz
  [project-root _ seed opts]
  (start-frotz-process project-root seed (assoc opts :debug? true)))

(defmethod start-process! :frotz-release
  [project-root _ seed opts]
  (start-frotz-process project-root seed (assoc opts :debug? false)))

(defn read-response!
  "Reads the response as a map with two values:
  * :response - string response, starting with the previous command's input prompt, ending with blank line
  * :prompt - :keystroke or :line"
  [process]
  (-> process :output-ch <!!))

(defn send-command!
  "Sends a player command to the process, blocking until a response to the command
  is available.  Returns the response (see read-response!)."
  ([process ^String command]
   (send-command! process command false))
  ([process ^String command keystroke?]
   (let [{:keys [^PrintWriter stdin-writer]} process]
     (if keystroke?
       (.print stdin-writer command)
       (.println stdin-writer command))
     (.flush stdin-writer)
     (read-response! process))))

(defn kill!
  "Kills the OS process and returns nil."
  [process]
  (let [{:keys [^Process process output-ch]} process]
    (when process
      (.destroyForcibly process))
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
