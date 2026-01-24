(ns dialog-tool.skein.routes.app
  (:require [cheshire.core :as json]
            [clj-simple-router.core :as router]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.ui.app :as ui.app]
            [dialog-tool.skein.ui.components.modal :as modal]
            [huff2.core :refer [html]]
            [starfederation.datastar.clojure.api :as d*]))

(defn- render-app
  ([request]
   (render-app request nil))
  ([request opts]
   {:status 200
    :body (html (ui.app/render-app request opts))}))

;;; Action handlers
;;; Each receives :signals (parsed Datastar signals) in the request

(defn- knot-id
  "Extracts the knot id from the first path parameter."
  [{:keys [path-params]}]
  (-> path-params first parse-long))

(defn- new-command
  "Adds a new command to the tree as a child of the active knot."
  [{:keys [*session signals] :as request}]
  (let [{:keys [newCommand]} signals
        command (some-> newCommand str string/trim not-empty)]
    (when command
      (swap! *session session/command! command))
    (render-app request {:scroll-to-new-command? true
                         :reset-command-input? true})))

(defn- bless-knot
  "Blesses the specified knot, copying its unblessed response to be the blessed response."
  [{:keys [*session] :as request}]
  (swap! *session session/bless (knot-id request))
  (render-app request))

(defn- bless-to-knot
  "Blesses all knots from root to the specified knot, inclusive."
  [{:keys [*session] :as request}]
  (swap! *session session/bless-to (knot-id request))
  (render-app request))

(defn- select-knot
  "Selects the specified knot, making it and its ancestors the active path."
  [{:keys [*session] :as request}]
  (swap! *session session/select-knot (knot-id request))
  (render-app request))

(defn- prepare-new-child
  "Prepares for adding a new child to the specified knot.
   Replays to the knot and deselects its children."
  [{:keys [*session] :as request}]
  (swap! *session session/prepare-new-child! (knot-id request))
  (render-app request {:scroll-to-new-command? true
                       :reset-command-input? true}))

(defn- open-edit-command
  "Opens the edit command modal for the specified knot."
  [{:keys [*session] :as request}]
  (let [id (knot-id request)
        tree (:tree @*session)
        knot (tree/get-knot tree id)
        command (:command knot)]
    {:status 200
     :body (html
            [:div#modal-container
             {:data-signals (json/generate-string {:editCommand command})}
             [modal/modal
              {:title "Edit Command"
               :content
               [:form {:data-on:submit__stop (str "@post('/action/edit-command/" id "')")}
                [:div.mb-4
                 [:label.block.text-sm.font-medium.text-gray-700.mb-2 {:for "edit-command-input"}
                  "Command:"]
                 [:input#edit-command-input
                  {:type "text"
                   :data-bind "editCommand"
                   :data-init "el.select()"
                   :class "w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"}]]
                [:div.flex.justify-end.gap-2
                 [:button.px-4.py-2.text-sm.font-medium.text-gray-700.bg-white.border.border-gray-300.rounded-md.hover:bg-gray-50
                  {:type "button"
                   :data-on:click__stop "@post('/action/dismiss-modal')"}
                  "Cancel"]
                 [:button.px-4.py-2.text-sm.font-medium.text-white.bg-blue-700.rounded-md.hover:bg-blue-800
                  {:type "submit"}
                  "OK"]]]}]])}))

(defn- edit-command
  "Submits the edited command for the knot and re-renders the app."
  [{:keys [*session signals] :as request}]
  (let [id (knot-id request)
        {:keys [editCommand]} signals
        command (some-> editCommand str string/trim not-empty)]
    (when command
      (swap! *session session/edit-command! id command))
    ;; Return both updated app and cleared modal - Datastar patches both by id
    {:status 200
     :body (html [:<>
                  (ui.app/render-app request {})
                  [:div#modal-container]])}))

(defn- dismiss-modal
  "Dismisses any open modal by clearing the modal-container."
  [request]
  {:status 200
   :body (html [:div#modal-container])})

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
   "POST /action/new-command" req
   (new-command req)

   "POST /action/bless/*" req
   (bless-knot req)

   "POST /action/bless-to/*" req
   (bless-to-knot req)

   "POST /action/select/*" req
   (select-knot req)

   "POST /action/new-child/*" req
   (prepare-new-child req)

   "POST /action/open-edit-command/*" req
   (open-edit-command req)

   "POST /action/edit-command/*" req
   (edit-command req)

   "POST /action/dismiss-modal" req
   (dismiss-modal req)))

(def action-handler
  "Handler for /action/* routes with signal parsing middleware applied."
  (-> action-routes
      router/router
      wrap-parse-signals))

(def routes
  (router/routes
   "GET /app" req
   (render-app req)))
