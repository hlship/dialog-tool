(ns dialog-tool.skein.service
  "Wraps the Skein session in an HTTP service, exposing static resources and an
  API."
  (:require [babashka.fs :as fs]
            [clj-commons.ansi :refer [pout]]
            [clojure.java.browse :as browse]
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
  
  root-dir is the directory to load the project from, or nil for the current
  working directory.

  The :seed option is only used if the skein file does not already exist;
  otherwise it is used (or, if omitted, a random seed is created).
  
  Likewise, the :engine comes from meta, then as supplied, then :dgdebug as a default.

  Does not join the service.

  Returns a function that will shutdown the service when invoked."
  [root-dir opts]
  (let [{:keys [skein-path port seed engine development-mode?]
         :or   {port 10140}} opts
        tree                (when (fs/exists? skein-path)
                              (sk.file/load-tree skein-path))
        seed'               (or (get-in tree [:meta :seed])
                                seed
                                (rand-int 100000))
        engine'             (or (get-in tree [:meta :engine])
                                engine
                                :dgdebug)
        start-process       #(sk.process/start-process! root-dir engine' seed')
        session             (if tree
                              (s/create-loaded! start-process skein-path tree)
                              (s/create-new! start-process skein-path engine seed))
        shutdown-fn         (hk/run-server service-handler-proxy
                                           {:port          port
                                            :ip            "localhost"
                                            :server-header "Dialog Skein Service"})
        shutdown-service-fn (fn []
                              (shutdown-fn)
                              (println "Shut down")
                              (when-not development-mode?
                                (System/exit 0)))]
    (reset! *session (assoc session
                            ;; Development mode is when testing/debugging the tool itself
                            :development-mode? development-mode?
                            ;; debug-enabled? is for users debugging their projects using dgdebug
                            :debug-enabled? (= engine' :dgdebug)))
    (reset! *shutdown shutdown-service-fn)
    {:shutdown-fn shutdown-service-fn
     :port        port}))

(comment

  @*session
  
  (-> @*session :debug-enabled?)
  
  (-> @*session :tree :dynamic (get 0))
  
  (@*shutdown)

  (start! "../sanddancer-dialog"
          {:engine            :dgdebug
           :skein-path        "../sanddancer-dialog/default.skein"
           :development-mode? true})

  (start! "../sanddancer-dialog"
          {:engine     :dgdebug
           :skein-path "/tmp/sd.skein"})


  (start! "../dialog-extensions/who"
          {:development-mode? true
           :skein-path        "../dialog-extensions/who/default.skein"})

  (start! "../dialog-extensions/who"
          {:skein-path "../dialog-extensions/who/frotz.skein"
           :seed       10101
           :engine :frotz})


  ;;
  )
