(ns dialog-tool.skein.handlers
  (:require [clj-simple-router.core :as router]
            [dialog-tool.skein.ui.diff :as diff]
            [ring.middleware.content-type :as content-type]
            [ring.middleware.params :as params]
            [huff2.core :as huff :refer [html]]
            [ring.util.response :as response]
            [clojure.string :as string]
            [selmer.parser :as s]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.ui.modals :as modals]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.ui.app :as ui.app]
            [dialog-tool.skein.ui.utils :as utils]
            [taoensso.timbre :refer [info]]
            [starfederation.datastar.clojure.adapter.http-kit2 :as hk-gen]))

(defn- expand-raw-string-body
  "huff returns a wrapper type, huff2.core.RawString, which is not directly compatible
  with http-kit; this middleware converts a RawString :body to a simple String."
  [f]
  (fn [request]
    (let [{:keys [body headers] :as response} (f request)]
      (if (huff/raw-string? body)
        (cond-> (update response :body str)
          (nil? (get headers "Content-Type"))
          (assoc-in [:headers "Content-Type"] "text/html"))
        response))))

(defn- wrap-with-response-logger
  [f]
  (fn [request]
    (let [{:keys [uri request-method]} request
          method        (-> request-method name string/upper-case)
          start-nanos   (System/nanoTime)
          response      (f request)
          elapsed-nanos (- (System/nanoTime) start-nanos)]
      (info (format "\r%s %4s %s (%,.1f ms)"
                       (or (:status response) "SSE")
                       method uri (/ elapsed-nanos 1e6)))
      response)))

(defn- wrap-not-found
  [f]
  (fn [request]
    (let [response (f request)]
      (or response
          (response/not-found (str "NOT FOUND: "
                                   (:uri request)))))))

(defn- log-errors
  [f]
  (fn [request]
    (try
      (f request)
      (catch Throwable e
        (println "Error: " e)
        {:status  500
         :headers {"content-type" "text/plain"}
         :body    (str "INTERNAL SERVER ERROR: " (ex-message e))}))))

(defn- wrap-signal->session
  "Applies relevant signals in the request to the session."
  [f]
  (fn [request]
    (let [{:keys [*session signals]} request
          {show-dynamic :showDynamic} signals]
      (when (some? show-dynamic)
        (swap! *session assoc :show-dynamic? show-dynamic))
      (f request))))

(defn- render-app
  ([session]
   (render-app session nil))
  ([session opts]
   {:status 200
    :body   (html
              [:<>
               (ui.app/render-app session opts)
               (ui.app/render-fab)])}))

;;; Action handlers
;;; Each receives :signals (parsed Datastar signals) in the request

(defn- knot-id
  "Extracts the knot id from the first path parameter."
  [{:keys [path-params]}]
  (-> path-params first parse-long))

(defn- new-command
  "Adds a new command to the tree as a child of the active knot."
  [{:keys [*session signals] :as request}]
  (swap! *session session/check-for-changed-sources)
  (let [{:keys [newCommand]} signals
        command (some-> newCommand str string/trim not-empty)]
    (utils/with-short-sse
      request
      (fn [sse-gen]
        (when command
          (swap! *session session/command! command)
          (utils/patch-elements! sse-gen
                                 (ui.app/render-app @*session {:scroll-to-new-command? true}))
          (utils/patch-signals! sse-gen {:newCommand ""}))))))

(defn- bless-knot
  "Blesses the specified knot, copying its unblessed response to be the blessed response."
  [{:keys [*session] :as request}]
  (render-app (swap! *session session/bless (knot-id request))
              {:flash "Blessed"}))

(defn- bless-to-knot
  "Blesses all knots from root to the specified knot, inclusive."
  [{:keys [*session] :as request}]
  (render-app (swap! *session session/bless-to (knot-id request))
              {:flash "Blessed to here"}))

(defn- replay-to-knot
  "Replays from the start to the specified knot."
  [{:keys [*session] :as request}] 
  (diff/clear-cache)
  (swap! *session session/check-for-changed-sources)
  (render-app (swap! *session session/replay-to! (knot-id request))
              {:flash "Replayed"}))

(defn- select-knot
  "Selects the specified knot, making it and its ancestors the active path, and scrolls to the selected knot."
  [{:keys [*session] :as request}]
  (let [id (knot-id request)]
    (render-app (swap! *session session/select-knot id)
                {:scroll-to-knot-id id})))

(defn- prepare-new-child
  "Prepares for adding a new child to the specified knot.
   Selected the knot and clears and focuses on the new command input."
  [{:keys [*session] :as request}]
  (swap! *session session/prepare-new-child! (knot-id request))
  (utils/with-short-sse
    request
    (fn [sse-gen]
      (utils/patch-elements! sse-gen (ui.app/render-app @*session {:scroll-to-new-command? true}))
      (utils/patch-signals! sse-gen {:newCommand ""}))))

(defn- render-edit-command-modal
  "Renders the edit command modal with optional error message."
  [id command error]
  {:status 200
   :body   (html [modals/edit-command id command error])})

(defn- open-edit-command
  "Opens the edit command modal for the specified knot."
  [{:keys [*session] :as request}]
  (let [id      (knot-id request)
        tree    (:tree @*session)
        knot    (tree/get-knot tree id)
        command (:command knot)]
    (render-edit-command-modal id command nil)))

(defn- edit-command
  "Submits the edited command for the knot and re-renders the app."
  [{:keys [*session signals] :as request}]
  (swap! *session session/check-for-changed-sources)
  (let [id       (knot-id request)
        {:keys [editCommand]} signals
        command  (some-> editCommand str string/trim)
        [error session'] (session/edit-command! @*session id command)]
    (reset! *session session')
    (if error
      ;; Error occurred - redisplay modal with error
      (render-edit-command-modal id command error)
      ;; Success - return both updated app and cleared modal
      (utils/with-short-sse
        request
        (fn [sse-gen]
          (utils/patch-elements! sse-gen
                                 [:<>
                                  (ui.app/render-app session' {})
                                  [:div#modal-container]])
          (utils/patch-signals! sse-gen {:editCommand nil}))))))

(defn- render-insert-parent-modal
  "Renders the insert parent modal with optional error message."
  [id command error]
  {:status 200
   :body   (html
             (modals/insert-parent id command error))})

(defn- open-insert-parent
  "Opens the insert parent modal for the specified knot."
  [request]
  (let [id (knot-id request)]
    (render-insert-parent-modal id "" nil)))

(defn- insert-parent
  "Submits the insert parent command for the knot and re-renders the app."
  [{:keys [*session signals] :as request}]
  (swap! *session session/check-for-changed-sources)
  (let [id       (knot-id request)
        {:keys [insertCommand]} signals
        command  (some-> insertCommand str string/trim not-empty)
        [error session'] (session/insert-parent! @*session id command)]
    (reset! *session session')
    (if error
      ;; Error occurred - redisplay modal with error
      (render-insert-parent-modal id command error)
      ;; Success - return both updated app and cleared modal
      (utils/with-short-sse
        request
        (fn [sse-gen]
          (utils/patch-signals! sse-gen {:insertCommand nil})
          (utils/patch-elements! sse-gen (html [:<>
                                                (ui.app/render-app @*session {})
                                                [:div#modal-container]])))))))

(defn- render-edit-label-modal
  "Renders the edit label modal with optional error message."
  [id label error]
  {:status 200
   :body   (html (modals/edit-label id label error))})

(defn- open-edit-label
  "Opens the edit label modal for the specified knot."
  [{:keys [*session] :as request}]
  (let [id    (knot-id request)
        tree  (:tree @*session)
        knot  (tree/get-knot tree id)
        label (or (:label knot) "")]
    (render-edit-label-modal id label nil)))

(defn- edit-label
  "Submits the edited label for the knot and re-renders the app."
  [{:keys [*session signals] :as request}]
  (let [id            (knot-id request)
        {:keys [editLabel]} signals
        label         (some-> editLabel str string/trim)
        tree          (:tree @*session)
        existing-knot (when-not (string/blank? label)
                        (tree/find-by-label tree label))
        is-duplicate? (and existing-knot (not= id (:id existing-knot)))]
    (if is-duplicate?
      ;; Duplicate found in a different knot - return modal with error
      (render-edit-label-modal id label (str "Label \"" label "\" is already used by another knot."))
      ;; No duplicate or same knot - proceed with update
      (utils/with-short-sse
        request
        (fn [sse-gen]
          (swap! *session session/label id label)
          (utils/patch-signals! sse-gen {:editLabel nil})
          (utils/patch-elements! sse-gen [:<>
                                          (ui.app/render-app @*session {})
                                          [:div#modal-container]]))))))

(defn- dismiss-modal
  "Dismisses any open modal by clearing the modal-container."
  [request]
  {:status 200
   :body   (html [:div#modal-container])})

(defn- undo
  "Undoes the last action by restoring the previous tree state."
  [{:keys [*session] :as request}]
  (render-app   (swap! *session session/undo)
                {:flash "Undo"}))

(defn- redo
  "Redoes the last undone action by restoring the next tree state."
  [{:keys [*session] :as request}]
  (render-app  (swap! *session session/redo) 
               {:flash "Redo"}))

(defn- save
  "Saves the current tree state to the file."
  [{:keys [*session] :as request}]
  (render-app   (swap! *session session/save!)
                {:flash "Saved"}))

(defn- replay-all
  "Replays to all leaf knots with SSE progress updates."
  [{:keys [*session] :as request}]
  (diff/clear-cache)
  (utils/with-short-sse
    request
    (fn [sse-gen]
      (let [initial-tree (:tree @*session)
            leaf-knots   (tree/leaf-knots initial-tree)
            total        (count leaf-knots)]
        ;; Capture initial state for undo (once for entire operation)
        (swap! *session session/capture-undo)
        (swap! *session session/check-for-changed-sources)
        
        ;; Replay to each leaf knot
        (doseq [[idx knot] (map-indexed vector leaf-knots)]
          (let [current (inc idx)
                {:keys [id label]} knot]
            ;; Update progress
            (utils/patch-elements!
              sse-gen
              (html (modals/progress
                      {:current   current
                       :total     total
                       :label     label
                       :operation "Replaying All"})))

            ;; Replay to this leaf (using do-replay-to! to avoid capturing undo)
            (swap! *session #(session/do-replay-to! % id))))

        ;; Close progress modal and render final state
        (utils/patch-elements!
          sse-gen
          (html [:<>
                 (ui.app/render-app @*session {:flash "Replay complete"})
                 [:div#modal-container]]))))))

(defn- delete-knot
  "Deletes the specified knot and all its descendants."
  [{:keys [*session] :as request}]
  (render-app (swap! *session session/delete (knot-id request))
              {:flash "Deleted"}))

(defn- show-dynamic-state
  [{:keys [*session] :as request}]
  (let [{:keys [dynamic-response]} (session/get-knot @*session (knot-id request))
        [_ trimmed] (string/split dynamic-response #"\n" 2)]
    {:status 200
     :body   (html
               (modals/dynamic-state trimmed))}))

(defn- splice-out-knot
  "Splices out the specified knot, reparenting its children."
  [{:keys [*session] :as request}]
  (let [[error session'] (session/splice-out! @*session (knot-id request))]
    (reset! *session session')
    (if error
      ;; TODO: Better way to present the error (a modal?)
      (render-app session' {:flash error})
      (render-app session' {:flash "Spliced out"}))))

(defn- close-and-shutdown
  "Closes the browser window and shuts down the service."
  [request]
  (let [{:keys [*shutdown]} request]
    (future
      ;; Give browser a moment to process the close command
      (Thread/sleep 200)
      ;; Shut down the service 
      (@*shutdown))
    {:status 200
     :body   (html [:<>
                    [:div#app]                              ; Clear the main body of the app
                    [modals/close-window]])}))

(defn- open-quit
  "Checks if session is dirty. If so, shows quit confirmation modal.
   If not dirty, proceeds directly to quit."
  [{:keys [*session] :as request}]
  (let [session @*session
        dirty?  (:dirty? session)]
    (if dirty?
      ;; Show confirmation modal
      {:status 200
       :body   (html [modals/quit-modal])}
      ;; Not dirty, quit immediately
      (close-and-shutdown request))))

(defn- save-and-quit
  "Saves the session and quits the service."
  [{:keys [*session] :as request}]
  (swap! *session session/save!)
  (close-and-shutdown request))

(defn- quit-without-saving
  "Quits the service without saving."
  [request]
  (close-and-shutdown request))

(defn- render-index
  "Renders the template index.html file."
  [{:keys [*session]}]
  (let [{:keys [development-mode?]} @*session]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (s/render-file "skein/index.html"
                             {:dev development-mode?})}))

(def ^:private routes
  (router/routes
    "GET /" req
    (render-index req)

    "POST /action/new-command" req
    (new-command req)

    "POST /action/bless/*" req
    (bless-knot req)

    "POST /action/bless-to/*" req
    (bless-to-knot req)

    "POST /action/replay-to/*" req
    (replay-to-knot req)

    "GET /action/select/*" req
    (select-knot req)

    "POST /action/new-child/*" req
    (prepare-new-child req)

    "GET /action/edit-command/*" req
    (open-edit-command req)

    "POST /action/edit-command/*" req
    (edit-command req)

    "GET /action/insert-parent/*" req
    (open-insert-parent req)

    "POST /action/insert-parent/*" req
    (insert-parent req)

    "GET /action/edit-label/*" req
    (open-edit-label req)

    "POST /action/edit-label/*" req
    (edit-label req)

    "POST /action/dismiss-modal" req
    (dismiss-modal req)

    "GET /action/undo" req
    (undo req)

    "GET /action/redo" req
    (redo req)

    "POST /action/save" req
    (save req)

    "POST /action/replay-all" req
    (replay-all req)

    "POST /action/delete/*" req
    (delete-knot req)

    "POST /action/splice-out/*" req
    (splice-out-knot req)

    "GET /action/quit" req
    (open-quit req)

    "POST /action/save-and-quit" req
    (save-and-quit req)

    "POST /action/quit-without-saving" req
    (quit-without-saving req)

    "GET /app" req
    (render-app (-> req :*session deref))

    "GET /action/dynamic/*" req
    (show-dynamic-state req)
 
    "GET /**" [path]
    ;; During local development, generated-resources/public/style.css will come from here
    ;; and everything else from resources/public.
    (response/resource-response path {:root "public"})))

(def service-handler
  "The main Ring handler for the Skein web service.

  Composes a router (from routes/routes) with middleware for:
  - Converting Huff RawString bodies to plain strings
  - Extracting Datastar signals from the incoming request
  - Returning 404 for unmatched routes
  - Setting Content-Type headers
  - Logging requests and responses"
  (-> (router/router routes)
      expand-raw-string-body
      wrap-not-found
      content-type/wrap-content-type
      wrap-signal->session
      utils/wrap-parse-signals
      params/wrap-params
      hk-gen/wrap-start-responding
      log-errors
      wrap-with-response-logger))
