(ns dialog-tool.skein.service
  "Wraps the Skein session in an HTTP service using Hyper for reactive
  server-rendered UI over Datastar/SSE."
  (:require [babashka.fs :as fs]
            [dialog-tool.skein.file :as sk.file]
            [dialog-tool.skein.process :as sk.process]
            [dialog-tool.skein.session :as s]
            [dialog-tool.skein.source-handlers :as source]
            [dialog-tool.skein.ui.app :as ui.app]
            [hyper.core :as h]
            [hyper.state :as state])
  (:import (java.net ServerSocket)))

;; The Skein session is stored in hyper's app-state atom under the :session key.
;; Handlers access it via (h/global-cursor :session).

(def ^:private routes
  [["/" {:name :skein
         :title "Dialog Skein"
         :get #'ui.app/skein-page}]
   ["/action/source/:id" {:hyper/disabled? true
                           :get #'source/view-source}]
   ["/action/source-preview/:id" {:hyper/disabled? true
                                   :get #'source/source-preview}]])

(defn- create-handler
  "Creates the Hyper Ring handler, seeding the skein session into app-state."
  [app-state*]
  (h/create-handler
   #'routes
   :app-state app-state*
   :static-resources "public"
   :datastar-script [:script {:type "module"
                              :src "/js/main.js"}]
   :head [[:link {:rel "icon" :type "image/x-icon" :href "/favicon.ico"}]
          [:link {:rel "stylesheet" :href "/style.css"}]]))

(defonce *app (atom nil))

;; Holds the hyper app-state atom for direct access (e.g. stop! cleanup)
(defonce *handler-app-state (atom nil))

(defn- free-port
  []
  (with-open [s (ServerSocket. 0)]
    (.getLocalPort s)))

(defn start!
  "Starts a service with the Skein for the given path, or a new empty skein
  if the path does not exist.
  
  If a port is not specified, a free port is identified and used.
  
  root-dir is the directory to load the project from, or nil for the current
  working directory.

  The :seed option is only used if the skein file does not already exist;
  otherwise it is used (or, if omitted, a random seed is created).
  
  Likewise, the :engine comes from meta, then as supplied, then :dgdebug as a default.

  Does not join the service.

  Returns the port opened."
  [root-dir opts]
  (let [{:keys [skein-path port seed engine development-mode? exit-when-shutdown?]
         :or   {exit-when-shutdown? true}} opts
        port' (or port (free-port))
        tree (when (fs/exists? skein-path)
               (sk.file/load-tree skein-path))
        seed' (or (get-in tree [:meta :seed])
                  seed
                  (rand-int 100000))
        engine' (or (get-in tree [:meta :engine])
                    engine
                    :dgdebug)
        start-process (fn [& [opts]] (sk.process/start-process! root-dir engine' seed' opts))
        session (if tree
                  (s/create-loaded! start-process skein-path tree)
                  (s/create-new! start-process skein-path engine' seed'))
        session' (assoc session
                        :development-mode? development-mode?
                        :debug-enabled? (= engine' :dgdebug)
                        :replay-on-launch? true
                        :exit-when-shutdown? exit-when-shutdown?)
        app-state* (atom (assoc-in (state/init-state) [:global :session] session'))]
    (reset! *handler-app-state app-state*)
    (reset! *app (h/start! (create-handler app-state*) {:port port'}))
    port'))

(defn stop!
  []
  (when-let [stop-fn @*app]
    ;; Kill the running process if any
    (when-let [session (get-in @*handler-app-state [:global :session])]
      (when-let [process (:process session)]
        (sk.process/kill! process)))
    (h/stop! stop-fn)
    (reset! *app nil)
    (println "Shut down")))

(comment

  @*app

  (-> @(:hyper/app-state @*app) :session)

  (stop!)

  (start! "../sanddancer-dialog"
          {:engine :dgdebug
           :skein-path "../sanddancer-dialog/default.skein"
           :port 10140
           :exit-when-shutdown? false
           :development-mode? false})

  (start! "../dialog-extensions/tree"
          {:engine :dgdebug
           :skein-path "../dialog-extensions/tree/default.skein"
           :port 10140
           :exit-when-shutdown? false
           :development-mode? true})

  (start! "../sanddancer-dialog"
          {:engine :dgdebug
           :skein-path "/tmp/sd.skein"
           :exit-when-shutdown? false})

  (start! "../dialog-extensions/who"
          {:development-mode? true
           :skein-path "../dialog-extensions/who/default.skein"
           :exit-when-shutdown? false})

  (start! "../dialog-extensions/who"
          {:skein-path "../dialog-extensions/who/frotz.skein"
           :port 10140
           :seed 10101
           :development-mode? true
           :exit-when-shutdown? false
           :engine :frotz})

;;
  )
