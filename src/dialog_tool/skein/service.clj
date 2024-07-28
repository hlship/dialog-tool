(ns dialog-tool.skein.service
  "Wraps the Skein session in an HTTP service, exposing static resources and an
  API."
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [dialog-tool.project-file :as pf]
            [dialog-tool.skein.process :as sk.process]
            [dialog-tool.skein.session :as s]
            [org.httpkit.server :as hk]))



^:clj-reload/keep
(def *session (atom nil))

^:clj-reload/keep
(def *shutdown (atom nil))

(def text-plain {"Content-Type" "text/plain"})

(defn- api-handler [method content-type body]
  {:status 200
   :headers text-plain

   :body   "NOT YET IMPLEMENTED"})

(defn- extension->content-type
  [uri]
  (let [ext (fs/extension uri)]
    ;; May need to add a few for image types, fonts, etc.
    (get {"css"  "text/css"
          "html" "text/html"
          "js"   "text/javascript"
          "json" "application/json"}
         ext
         "text/plain")))

(defn- resource-handler
  [uri]
  (let [r (io/resource (str "public" uri))]
    (if (some? r)
      {:status  200
       :headers {"Content-Type" (extension->content-type uri)}
       :body    (io/input-stream r)}
      {:status 404
       :headers text-plain
       :body   (str "NOT FOUND: " uri)})))

(defn- service-handler
  [request]
  (let [{:keys [uri request-method content-type body]} request]
    (println "%s %s" (-> request-method name string/upper-case) uri)
    (if (= uri "/api")
      (api-handler request-method content-type body)
      (resource-handler uri))))

(defn start-service!
  "Starts a service with an empty skein."
  [project skein-path opts]
  (let [{:keys [port debugger-path seed join?]
         :or   {port 10140}} opts
        process (sk.process/start-debug-process! debugger-path project seed)
        _ (reset! *session (s/create-new! process skein-path))
        shutdown-fn (hk/run-server service-handler
                                   {:port          port
                                    :ip            "localhost"
                                    :server-header "Dialog Skein Service"})
        blocker (promise)]
    (reset! *shutdown
            (fn []
              (shutdown-fn)
              (deliver blocker true)
              (println "Shut down")))
    (when join?
      @blocker)))

(comment
  (start-service!
    (pf/read-project "../sanddancer-dialog")
    "target/game.skein"
    7363521)

  (@*shutdown)


  @*session
  (let [project (pf/read-project "../sanddancer-dialog")
        process (sk.process/start-debug-process! nil project 7363521)
        session (s/create-new! process "target/game.skein")]
    (reset! *session session))

  (->> "../sanddancer-dialog/tests/complete/honor.txt"
       slurp
       string/split-lines
       (run! #(swap! *session s/command! %)))


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

