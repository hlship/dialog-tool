(ns dialog-tool.skein.service
  "Wraps the Skein session in an HTTP service using Hyper for reactive
  server-rendered UI over Datastar/SSE."
  (:require [babashka.fs :as fs]
            [clj-commons.ansi :refer [perr]]
            [dialog-tool.skein.file :as sk.file]
            [dialog-tool.skein.process :as sk.process]
            [dialog-tool.skein.search :as search]
            [dialog-tool.skein.session :as s]
            [dialog-tool.skein.source-handlers :as source]
            [dialog-tool.skein.ui.actions :as actions]
            [dialog-tool.skein.ui.app :as ui.app]
            [hyper.context :as context]
            [hyper.core :as h]
            [hyper.state :as state])
  (:import (java.net ServerSocket)))

;; The Skein session is stored in hyper's app-state atom under the :session key.
;; Handlers access it via (session-cursor).

;; TODO: Revisit this; does not appear to be available during action requests.
#_(defn log-action-wrapper
    [handler]
    (fn [req]
      (when-let [action-name context/*action-name*]
        (perr "request action: " action-name))
      (handler req)))

(def ^:private routes
  [["/" {:name  :skein
         :title "Dialog Skein"
         :get   #'ui.app/skein-page}]
   ["/action/source/:id" {:hyper/disabled? true
                          :get             source/view-source}]
   ["/action/source-preview/:id" {:hyper/disabled? true
                                  :get             source/source-preview}]])

(defn- create-handler
  "Creates the Hyper Ring handler, seeding the skein session into app-state."
  [*app-state]
  (h/create-handler
    #'routes
    :app-state *app-state
    ;; :render-middleware [log-action-wrapper]
    :static-resources "public"
    :datastar-script [:script {:type "module"
                               :src  "/js/main.js"}]
    :head [[:link {:rel "icon" :type "image/x-icon" :href "/favicon.ico"}]
           [:link {:rel "stylesheet" :href "/style.css"}]]))

;; The hyper app-state atom. Passed directly to create-handler.
;; The shutdown fn is stored at [:global :shutdown-fn].
(defonce *app (atom nil))

(defn- free-port
  []
  (with-open [s (ServerSocket. 0)]
    (.getLocalPort s)))

(defn stop!
  "Stops the running service, kills the game process, and resets *app."
  []
  (when-let [shutdown (get-in @*app [:global :shutdown-fn])]
    (shutdown)
    (reset! *app nil)))

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
        port'         (or port (free-port))
        tree          (when (fs/exists? skein-path)
                        (sk.file/load-tree skein-path))
        seed'         (or (get-in tree [:meta :seed])
                          seed
                          (rand-int 100000))
        engine'       (or (get-in tree [:meta :engine])
                          engine
                          :dgdebug)
        start-process (fn [& [opts]] (sk.process/start-process! root-dir engine' seed' opts))
        session       (if tree
                        (s/create-loaded! start-process skein-path tree)
                        (s/create-new! start-process skein-path engine' seed'))
        session'      (assoc session
                             ;; This is used to break a cyclic dependencies between
                             ;; the modals and actions namespaces
                             :replay-all actions/replay-all
                             :development-mode? development-mode?
                             :debug-enabled? (= engine' :dgdebug)
                             :loading? (nil? tree)
                                       :replay-on-launch? true)
        stop-server   (do
                        (reset! *app (-> (state/init-state)
                                         (assoc-in [:global :session] session')))
                        (h/start! (create-handler *app) {:port port'}))
        shutdown-fn   (fn []
                        ;; Give hyper a little time to get last updates to browser.
                        (do
                          (Thread/sleep 500)
                          (h/stop! stop-server)
                          (sk.process/kill! (get-in @*app [:global :session :process]))
                          (search/close!)

                          (println "Shut down")

                          (when (and exit-when-shutdown? (not development-mode?))
                            (System/exit 0))))]
    ;; This is not what cursors are really intended for, but it works for this case.
    (swap! *app assoc-in [:global :shutdown-fn] shutdown-fn)
    port'))
