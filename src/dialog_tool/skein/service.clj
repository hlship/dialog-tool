(ns dialog-tool.skein.service
  "Wraps the Skein session in an HTTP service, exposing static resources and an
  API."
  (:require [babashka.fs :as fs]
            [dialog-tool.project-file :as pf]
            [dialog-tool.skein.file :as sk.file]
            [dialog-tool.skein.process :as sk.process]
            [dialog-tool.skein.session :as s]
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
   (assoc request :*session *session
          :*shutdown *shutdown)))

(defn start!
  "Starts a service with the Skein for the given path, or a new empty skein
  if the path does not exist.

  The :seed option is only used if the skein file does not already exist;
  otherwise it is used (or, if omitted, a random seed is created).

  Does not join the service.

  Returns a function that does shutdown the service."
  [project skein-path opts]
  (let [{:keys [port seed engine]
         :or   {port 10140}} opts
        tree (when (fs/exists? skein-path)
               (sk.file/load-tree skein-path))
        seed' (or (get-in tree [:meta :seed])
                  seed
                  (rand-int 100000))
        engine' (or (get-in tree [:meta :engine])
                    engine
                    :dgdebug)
        process (sk.process/start-process! project engine' seed')
        session (if tree
                  (s/create-loaded! process skein-path tree)
                  (s/create-new! process skein-path engine seed))
        shutdown-fn (hk/run-server service-handler-proxy
                                   {:port          port
                                    :ip            "localhost"
                                    :server-header "Dialog Skein Service"})
        shutdown-service-fn (fn []
                              (shutdown-fn)
                              (println "Shut down"))]
    (reset! *session session)
    (reset! *shutdown shutdown-service-fn)
    {:shutdown-fn shutdown-service-fn
     :port        port}))

(comment

  @*session

  (@*shutdown)


  (start! (pf/read-project "../sanddancer-dialog")
          "../sanddancer-dialog/default.skein"
          {:engine :dgdebug})
           
    (start! (pf/read-project "../sanddancer-dialog")
          "/tmp/sd.skein"
          {:engine :dgdebug})


  (start! (pf/read-project "../dialog-extensions/who")
          "../dialog-extensions/who/default.skein"
          nil)

  (start! (pf/read-project "../dialog-extensions/who")
          "../dialog-extensions/who/frotz.skein"
          {:seed   10101
           :engine :frotz})
  

  ;;
  )
