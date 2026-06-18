(ns dialog-tool.skein.process
  "Management of a sub-process running the Dialog debugger or dumb frotz,
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

(defn- gen-input-scanner
  [tags result-ch post-process]
  (let [[line-input-char keystroke-input-char line-input-suffix] tags
        line-input      (str "\n" line-input-char " " line-input-suffix)
        keystroke-input (str keystroke-input-char " ")]
    (fn [sb]
      #_ (when false
        (println "Input:")
        (run! prn (-> sb str string/split-lines))
        (println "---------------------------"))
      (let [response          (str sb)
            input-prompt?     (string/ends-with? response line-input)
            keystroke-prompt? (and (not input-prompt?)
                                   ;; This is overkill for dgdebug (which outputs a newline before its
                                   ;; keystroke input prompt) but necessaryf or dfrotz.
                                   (-> response
                                       string/split-lines
                                       last
                                       (string/starts-with? keystroke-input)))]
        (when (or input-prompt?
                  keystroke-prompt?)
          (>!! result-ch
               (post-process response))
          (.setLength sb 0)
          (.append sb "  ")
          ;; Put prompt back into the SB
          (when input-prompt?
            (.append sb "> ")))))))

(defn- trim-returns
  [s]
  (string/replace s "\r" ""))

(defn- remove-tail
  [value lines]
  (if (= value (last lines))
    (recur value (butlast lines))
    lines))

(defn- gen-parse-tagged-lines
  [[_ keystroke-input-char]]
  (let [keystroke-input (str keystroke-input-char " ")]
    (fn [response]
      (let [raw-lines         (-> response trim-returns string/split-lines)
            lines             (->> raw-lines
                                   ;; This is output by dfrotz at startup, even with -q
                                   (drop-while #(= % "Line-type display ON"))
                                   (drop-while #(= % ""))   ; Initial response can start with empty string for first line
                                   ;; Most output line start with two spaces
                                   ;; The final output line is '>' or ')' (for dgdebug) or 'T' and ')' (for dfrotz), and a space.
                                   ;; keystroke prompt: dgdebug outputs a newline and ") " on its own line.
                                   ;; dfrotz outputs ") " as a prefix to the last line (which is not blank).
                                   (mapv #(subs % 2))
                                   (drop-while #(= % "")) 
                                   ;; Remove the prompt (if not a keystroke prompt) from this response; we hack it
                                   ;; as a prefix of the next response.
                                   (remove-tail "")
                                   (remove-tail "> "))
            keystroke-prompt? (-> raw-lines
                                  last
                                  (string/starts-with? keystroke-input))
            response'         (string/replace-first (string/join "\n" lines)
                                                    ;; dgdebug (and maybe dfrotz) starts each new response with an SGR reset,
                                                    ;; even when it isn't needed.
                                                    #"^\u001b\[0m" "")]
        ;; We need each response to end with a newline even when it's a keypress prompt that doesn't
        ;; inclde one (this is needed for consistency writing to and reading from a skein file).
        {:response (cond-> response'
                     (not (string/ends-with? response' "/n")) (str "\n"))
         :prompt   (if keystroke-prompt? :keystroke :line)}))))

(def ^:private buffer-size 100000)

(defn- read-loop
  [^BufferedReader reader ^StringBuffer sb tags result-ch]
  (let [^char/1 buffer (char-array buffer-size)
        post-process   (gen-parse-tagged-lines tags)
        scanner        (gen-input-scanner tags result-ch post-process)]
    (loop []
      ;; Only read as much as is ready, so that we don't block at a bad time. We need to read
      ;; up to the prompt (which will not have a trailing new line). Reading past the prompt will block.
      (let [n-read (try
                     (.read reader buffer 0 buffer-size)
                     (catch IOException _
                       ;; Process died
                       -1))]
        (if (neg? n-read)
          ;; Process died, pipe closed -- this can happen when there are errors in
          ;; the source code.  Provide as much as was read:
          (do
            (>!! result-ch (-> sb str post-process))
            (close! result-ch))
          (do
            (.append sb buffer 0 (int n-read))
            ;; Generally, dgdebug (or frotz) is blazing fast, and so once we get any input
            ;; we get a very complete response, but maybe someone's writing a novel
            ;; and 10K isn't big enough to get it in a single gulp.
            (scanner sb)
            (recur)))))))

(defn- command-loop
  [^PrintWriter stdin-writer ^StringBuffer sb echo-commands? command-ch]
  (loop []
    (when-let [{:keys [command keystroke?]} (<!! command-ch)]
      (when (and echo-commands? (not keystroke?))
        (.append sb command)
        (.append sb "\n"))
      ;; Ok, at this point, echo-commands? is really just "hack for dfrotz".
      ;; From what I can tell, dfrotz needs a newline, even after single keystrokes.
      (if (or (not keystroke?) echo-commands?)
        (.println stdin-writer command)
        (.print stdin-writer command))
      (.flush stdin-writer)
      ;; And wait for next
      (recur))))

(defn- start!
  [^List cmd opts]
  (let [{:keys [pre-flight tags echo-commands?]} opts
        ;; pre-flight is optional, used to build the .zblorb file for example,
        ;; so it must be before starting the process.
        _             (when pre-flight
                        (pre-flight))
        process       (-> (ProcessBuilder. cmd)
                          (.redirectErrorStream true)
                          .start)
        stdout-reader (.inputReader process)
        command-ch    (chan)
        result-ch     (chan)
        stdin-writer  (-> process .outputWriter PrintWriter.)
        ;; Need StringBuffer; shared across threads
        sb            (new StringBuffer (int buffer-size))]
    (env/debug-command cmd)
    (doto (Thread.
            ^Runnable #(read-loop stdout-reader sb tags result-ch)
            "skein process output reader")
      (.setDaemon true)
      .start)
    (doto (Thread.
            ^Runnable #(command-loop stdin-writer sb echo-commands? command-ch)
            "skein process input writer")
      (.setDaemon true)
      .start)
    {:process    process
     :result-ch  result-ch
     :command-ch command-ch}))

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
     (start! cmd {:tags [">" ")"]}))))

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
                     ;; Enable two character prefix on each line to identify kind (text, prompt, keyinput)
                     "-r" "lt"
                     ;; Although dfortz has "-f ansi", when enabled
                     ;; it is closer to a ncurses kind of output,
                     ;; with right padding of lines, and ANSI 
                     ;; Erase in Line (CSI K) sequences.
                     "-f" "normal"
                     ;; So, validate your formatting using dgdebug,
                     ;; and validate your zcode with dfrotz.
                     "-s" (str seed)
                     "-w" "-1"
                     (str path)]]
    (fs/create-dirs output-dir)
    (start! cmd
            {:pre-flight     pre
             :echo-commands? true
             :tags           ["T" ")" "> "]})))

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
  (-> process :result-ch <!!))

(defn send-command!
  "Sends a player command to the process, blocking until a response to the command
  is available.  Returns the response (see read-response!)."
  ([process ^String command]
   (send-command! process command false))
  ([process ^String command keystroke?]
   (let [{:keys [command-ch]} process]
     (>!! command-ch {:command    command
                      :keystroke? keystroke?})
     (read-response! process))))

(defn kill!
  "Kills the OS process and returns nil."
  [process]
  (let [{:keys [^Process process result-ch command-ch]} process]
    (when process
      (.destroyForcibly process))
    (when command-ch
      (close! command-ch))
    (when result-ch
      (close! result-ch)))
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
