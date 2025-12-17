(ns dialog-tool.skein.routes.app
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clj-simple-router.core :as router]
            [clojure.string :as string]
            [huff2.core :refer [html]]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.ui.app :as ui.app]
            [starfederation.datastar.clojure.api :as d*]
            [huff2.core :as h]))

(defn- render-app
  [request]
  {:status 200
   :body (html (ui.app/render-app request))})

;;; Action handlers
;;; Each receives :signals (parsed Datastar signals) in the request

(defn- new-node
  "Adds a new node to the tree as a child of the active knot."
  [{:keys [*session signals] :as request}]
  (let [{:keys [newCommand]} signals
        command (some-> newCommand str string/trim not-empty)]
    (when command
      (swap! *session session/command! command))
    (render-app request)))

(defn- bless-node
  "Blesses the specified knot, copying its unblessed response to be the blessed response."
  [{:keys [*session path-params] :as request}]
  (let [knot-id (-> path-params first parse-long)]
    (swap! *session session/bless knot-id)
    (render-app request)))

(defn- wrap-parse-signals
  "Middleware that parses Datastar signals and adds them to the request as :signals."
  [handler]
  (fn [request]
    (let [data (d*/get-signals request)
          signals (if (string? data)
                    (json/parse-string data true)
                    (with-open [r (io/reader data)] 
                                (json/parse-stream r true)))] 
      (handler (assoc request :signals signals)))))

(def ^:private action-routes
  (router/routes
    "POST /action/new-node" req
    (new-node req)
    
    "POST /action/bless/*" req
    (bless-node req)))

(def action-handler
  "Handler for /action/* routes with signal parsing middleware applied."
  (-> action-routes
      router/router
      wrap-parse-signals))

(def routes
  (router/routes
    "GET /app" req
    (render-app req)))

