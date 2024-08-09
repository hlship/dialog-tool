(ns dialog-tool.skein.service
  "Wraps the Skein session in an HTTP service, exposing static resources and an
  API."
  (:require [babashka.fs :as fs]
            [clojure.string :as string]
            [dialog-tool.project-file :as pf]
            [dialog-tool.skein.file :as sk.file]
            [dialog-tool.skein.process :as sk.process]
            [dialog-tool.skein.session :as s]
            [dialog-tool.skein.tree :as tree]
            [org.httpkit.server :as hk]))

^:clj-reload/keep
(def *session (atom nil))

^:clj-reload/keep
(def *shutdown (atom nil))

(defn- service-handler-proxy
  [request]
  ;; This is to allow code reloading to work correctly without restarting
  ;; the service.
  ((requiring-resolve 'dialog-tool.skein.handlers/service-handler)
   (assoc request :*session *session)))

(defn start!
  "Starts a service with the Skein for the given path, or a new empty skein
  if the path does not exist."
  [project skein-path opts]
  (let [{:keys [port seed join?]
         :or   {port 10140}} opts
        tree (when (fs/exists? skein-path)
               (sk.file/load-skein skein-path))
        seed (or (get-in tree [:meta :seed])
                 seed
                 (rand-int 10000))
        process (sk.process/start-debug-process! project seed)
        session (if tree
                  (s/create-loaded! process skein-path tree)
                  (s/create-new! process skein-path))
        shutdown-fn (hk/run-server service-handler-proxy
                                   {:port          port
                                    :ip            "localhost"
                                    :server-header "Dialog Skein Service"})
        blocker (promise)]
    (reset! *session session)
    (reset! *shutdown
            (fn []
              (shutdown-fn)
              (deliver blocker true)
              (println "Shut down")))
    (when join?
      @blocker)))


(comment

  @*session

  (start! (pf/read-project "../sanddancer-dialog")
          "target/game.skein"
          nil)                                             ; does not join!

  (@*shutdown)

  (->> "../sanddancer-dialog/tests/complete/honor.txt"
       slurp
       string/split-lines
       (run! #(swap! *session s/command! %)))

  (tree/->wire (:tree @*session))

  (-> @*session :tree :nodes (get 0))

  (swap! *session s/command! "open glove")
  (time (swap! *session s/command! "x pack"))
  (swap! *session s/command! "smoke")
  (swap! *session s/command! "cigarette")
  (swap! *session s/bless 0)
  (swap! *session s/bless-all)
  (swap! *session s/restart!)
  (swap! *session s/command! "x truck")
  (time (swap! *session s/replay-to! 1722112918940))
  (swap! *session s/save!)
  (swap! *session s/kill!)
  )

