(ns dialog-tool.skein.handlers
  (:require [clj-simple-router.core :as router]
            [ring.middleware.content-type :as content-type]
            [ring.middleware.params :as params]
            [huff2.core :as huff :refer [html]]
            [ring.util.response :as response]
            [clojure.string :as string]
            [selmer.parser :as s]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.trace :as trace]
            [dialog-tool.skein.syntax :as syntax]
            [dialog-tool.skein.ui.modals :as modals]
            [dialog-tool.skein.tree :as tree]
            [dialog-tool.skein.ui.trace-view :as trace-view]
            [dialog-tool.skein.ui.app :as ui.app]
            [dialog-tool.skein.ui.utils :as utils]
            [dialog-tool.env :as env]
            [dialog-tool.project-file :as pf]
            [babashka.fs :as fs]
            [clj-commons.ansi :refer [pout]]
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
          method (-> request-method name string/upper-case)
          start-nanos (System/nanoTime)
          response (f request)
          elapsed-nanos (- (System/nanoTime) start-nanos)]
      (pout [:faint (format "%tT" (System/currentTimeMillis))]
            " "
            [{:width 3} (or (:status response) "SSE")]
            " "
            [{:width 4} method]
            " "
            uri
            (format " (%,.1f ms)"
                    (/ elapsed-nanos 1e6)))
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
        {:status 500
         :headers {"content-type" "text/plain"}
         :body (str "INTERNAL SERVER ERROR: " (ex-message e))}))))

(defn- wrap-signal->session
  "Applies relevant signals in the request to the session."
  [f]
  (fn [request]
    (let [{:keys [*session signals]} request
          {show-dynamic :showDynamic
           fixed-width :fixedWidth} signals]
      (when (some? show-dynamic)
        (swap! *session assoc :show-dynamic? show-dynamic))
      (when (some? fixed-width)
        (swap! *session assoc :fixed-width? fixed-width))
      (f request))))

(defn- normalize-input
  [s]
  (-> s str string/trim (string/replace #"\s+" " ")))

(defn- render-app
  ([request]
   (render-app request nil))
  ([request opts]
   (utils/with-short-sse
     request
     (fn [sse-gen]
       (ui.app/render-app sse-gen @(:*session request) opts)))))

;;; Action handlers
;;; Each receives :signals (parsed Datastar signals) in the request

(defn- knot-id
  "Extracts the knot id from the first path parameter."
  [{:keys [path-params]}]
  (-> path-params first parse-long))

(defn- new-command
  "Adds a new command to the tree as a child of the specified knot."
  [{:keys [*session signals] :as request}]
  (swap! *session session/check-for-changed-sources)
  (let [{:keys [newCommand]} signals
        command (some-> newCommand normalize-input)
        parent-knot-id (knot-id request)]
    (utils/with-short-sse
      request
      (fn [sse-gen]
        (when command
          (swap! *session session/command! parent-knot-id command)
          ;; This needs to be done first, otherwise looks like a race condition
          ;; that can result in the text carrying over instead of being blanked
          ;; out.
          (utils/patch-signals! sse-gen {:newCommand ""})
          (ui.app/render-app sse-gen @*session {:scroll-to-new-command? true}))))))

(defn- bless-knot
  "Blesses the specified knot, copying its unblessed response to be the blessed response."
  [{:keys [*session] :as request}]
  (swap! *session session/bless (knot-id request))
  (render-app request {:flash "Blessed"}))

(defn- bless-to-knot
  "Blesses all knots from root to the specified knot, inclusive."
  [{:keys [*session] :as request}]
  (swap! *session session/bless-to (knot-id request))
  (render-app request {:flash "Blessed to here"}))

(defn- replay-to-knot
  "Replays from the start to the specified knot."
  [{:keys [*session] :as request}]
  (swap! *session session/check-for-changed-sources)
  (swap! *session session/replay-to! (knot-id request))
  (render-app request {:flash "Replayed"}))

(defn- select-knot
  "Selects the specified knot, making it and its ancestors the active path, and scrolls to the selected knot."
  [{:keys [*session] :as request}]
  (let [id (knot-id request)]
    (swap! *session session/select-knot id)
    (render-app request {:scroll-to-knot-id id})))

(defn- prepare-new-child
  "Prepares for adding a new child to the specified knot.
   Selected the knot and clears and focuses on the new command input."
  [{:keys [*session] :as request}]
  (swap! *session session/prepare-new-child! (knot-id request))
  (utils/with-short-sse
    request
    (fn [sse-gen]
      (ui.app/render-app sse-gen @*session {:scroll-to-new-command? true})
      (utils/patch-signals! sse-gen {:newCommand ""}))))

(defn- render-edit-command-modal
  "Renders the edit command modal with optional error message."
  [id command error]
  {:status 200
   :body (html [modals/edit-command id command error])})

(defn- open-edit-command
  "Opens the edit command modal for the specified knot."
  [{:keys [*session] :as request}]
  (let [id (knot-id request)
        tree (:tree @*session)
        knot (tree/get-knot tree id)
        command (:command knot)]
    (render-edit-command-modal id command nil)))

(defn- edit-command
  "Submits the edited command for the knot and re-renders the app."
  [{:keys [*session signals] :as request}]
  (swap! *session session/check-for-changed-sources)
  (let [id (knot-id request)
        {:keys [editCommand]} signals
        command (some-> editCommand str string/trim)
        [error session'] (session/edit-command! @*session id command)]
    (reset! *session session')
    (if error
      ;; Error occurred - redisplay modal with error
      (render-edit-command-modal id command error)
      ;; Success - return both updated app and cleared modal
      (utils/with-short-sse
        request
        (fn [sse-gen]
          (ui.app/render-app sse-gen session' {})
          (utils/patch-elements! sse-gen [:div#modal-container])
          (utils/patch-signals! sse-gen {:editCommand nil}))))))

(defn- render-insert-parent-modal
  "Renders the insert parent modal with optional error message."
  [id command error]
  {:status 200
   :body (html
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
  (let [id (knot-id request)
        {:keys [insertCommand]} signals
        command (some-> insertCommand str string/trim not-empty)
        [error session'] (session/insert-parent! @*session id command)]
    (reset! *session session')
    (if error
      ;; Error occurred - redisplay modal with error
      (render-insert-parent-modal id command error)
      ;; Success - return both updated app and cleared modal
      (utils/with-short-sse
        request
        (fn [sse-gen]
          (ui.app/render-app sse-gen @*session {})
          (utils/patch-elements! sse-gen [:div#modal-container])
          (utils/patch-signals! sse-gen {:insertCommand nil}))))))

(defn- render-edit-label-modal
  "Renders the edit label modal with optional error message."
  [id label locked error]
  {:status 200
   :body (html (modals/edit-label id label locked error))})

(defn- open-edit-label
  "Opens the edit label modal for the specified knot."
  [{:keys [*session] :as request}]
  (let [id (knot-id request)
        tree (:tree @*session)
        knot (tree/get-knot tree id)
        label (or (:label knot) "")
        locked (boolean (:locked knot))]
    (render-edit-label-modal id label locked nil)))

(defn- edit-label
  "Submits the edited label for the knot and re-renders the app."
  [{:keys [*session signals] :as request}]
  (let [id (knot-id request)
        {:keys [editLabel editLocked]} signals
        label (some-> editLabel normalize-input)
        locked? (boolean editLocked)
        tree (:tree @*session)
        existing-knot (when-not (string/blank? label)
                        (tree/find-by-label tree label))
        is-duplicate? (and existing-knot (not= id (:id existing-knot)))]
    (if is-duplicate?
      ;; Duplicate found in a different knot - return modal with error
      (render-edit-label-modal id label locked? (str "Label \"" label "\" is already used by another knot."))
      ;; No duplicate or same knot - proceed with update
      (utils/with-short-sse
        request
        (fn [sse-gen]
          (swap! *session session/label id label locked?)
          (ui.app/render-app sse-gen @*session {})
          (utils/patch-elements! sse-gen [:div#modal-container])
          (utils/patch-signals! sse-gen {:editLabel nil :editLocked nil}))))))

(defn- dismiss-modal
  "Dismisses any open modal by clearing the modal-container."
  [{:keys [*session]}]
  (swap! *session dissoc :continue :trace)
  {:status 200
   :body (html [:div#modal-container])})

(defn- undo
  "Undoes the last action by restoring the previous tree state."
  [{:keys [*session] :as request}]
  (swap! *session session/undo)
  (render-app request {:flash "Undo"}))

(defn- redo
  "Redoes the last undone action by restoring the next tree state."
  [{:keys [*session] :as request}]
  (swap! *session session/redo)
  (render-app request {:flash "Redo"}))

(defn- save
  "Saves the current tree state to the file."
  [{:keys [*session] :as request}]
  (swap! *session session/save!)
  (render-app request {:flash "Saved"}))

(defn- replay-all
  "Replays to all leaf knots with SSE progress updates."
  [{:keys [*session] :as request}]
  (utils/with-short-sse
    request
    (fn [sse-gen]
      (let [initial-tree (:tree @*session)
            leaf-knots (tree/leaf-knots initial-tree)
            total (count leaf-knots)]
        ;; Capture initial state for undo (once for entire operation)
        (swap! *session session/capture-undo)
        (swap! *session session/check-for-changed-sources)

        ;; Set continue flag so cancel can stop the loop
        (swap! *session assoc :continue true)

        ;; Replay to each leaf knot, checking :continue between iterations
        (loop [remaining (map-indexed vector leaf-knots)]
          (when (and (seq remaining) (:continue @*session))
            (let [[idx knot] (first remaining)
                  current (inc idx)
                  {:keys [id label]} knot]
              ;; Update progress
              (utils/patch-elements!
               sse-gen
               (html (modals/progress
                      {:current current
                       :total total
                       :label label
                       :operation "Replaying All"})))

              ;; Replay to this leaf (using do-replay-to! to avoid capturing undo)
              (swap! *session #(session/do-replay-to! % id))

              (recur (rest remaining)))))

        (let [cancelled? (not (:continue @*session))
              flash (if cancelled? "Replay cancelled" "Replay complete")]
          ;; Clear the continue flag
          (swap! *session dissoc :continue)

          ;; Close progress modal and render final state
          (ui.app/render-app sse-gen @*session {:flash flash})
          (utils/patch-elements! sse-gen [:div#modal-container]))))))

(defn- delete-knot
  "Deletes the specified knot and all its descendants."
  [{:keys [*session] :as request}]
  (let [[error session'] (session/delete! @*session (knot-id request))]
    (reset! *session session')
    (if error
      (render-app request {:flash {:message error :type :error}})
      (render-app request {:flash "Deleted"}))))

(defn- show-dynamic-state
  [{:keys [*session] :as request}]
  (let [{:keys [dynamic-response]} (session/get-knot @*session (knot-id request))
        [_ trimmed] (string/split dynamic-response #"\n" 2)]
    {:status 200
     :body (html
            (modals/dynamic-state trimmed))}))

(defn- render-trace-modal
  "Renders the full trace modal from the current session's :trace state."
  [request]
  (let [{:keys [*session]} request
        trace-state (:trace @*session)]
    {:status 200
     :body (html (modals/trace-modal trace-state))}))

(defn- render-trace-results
  "Renders just the trace results area (for search/toggle/expand/collapse updates
   that should not disturb the search input or modal chrome)."
  [request]
  (let [{:keys [*session]} request
        trace-state (:trace @*session)]
    {:status 200
     :body (html (trace-view/render-trace-results trace-state))}))

(defn- trace-knot
  "Runs the knot's command with tracing enabled and displays the trace modal."
  [{:keys [*session] :as request}]
  (swap! *session session/check-for-changed-sources)
  (let [id (knot-id request)
        command (:command (session/get-knot @*session id))
        [trace-response session'] (session/trace-command! @*session id)
        _ (reset! *session session')
        parsed (trace/parse-trace trace-response)
        tree (trace/build-tree parsed)
        trace-state {:tree tree
                     :expanded #{}
                     :search ""
                     :node-count (trace/count-nodes tree)
                     :command command}]
    (swap! *session assoc :trace trace-state)
    (render-trace-modal request)))

(defn- trace-toggle
  "Toggles a node's expanded/collapsed state in the trace tree."
  [{:keys [*session] :as request}]
  (let [path (-> request :path-params first)]
    (swap! *session update-in [:trace :expanded]
           (fn [expanded]
             (if (contains? expanded path)
               (disj expanded path)
               (conj (or expanded #{}) path))))
    (render-trace-results request)))

(defn- trace-search
  "Updates the search term and expands paths to matching nodes."
  [{:keys [*session signals] :as request}]
  (let [{:keys [traceSearch]} signals
        search (or traceSearch "")]
    (swap! *session
           (fn [session]
             (let [tree (get-in session [:trace :tree])
                   expanded (if (string/blank? search)
                              #{}
                              (let [marked (trace/search-tree tree search)]
                                (trace-view/collect-matching-paths marked)))]
               (-> session
                   (assoc-in [:trace :search] search)
                   (assoc-in [:trace :expanded] expanded)))))
    (render-trace-results request)))

(defn- trace-expand-all
  "Expands all nodes in the trace tree."
  [{:keys [*session] :as request}]
  (let [tree (get-in @*session [:trace :tree])
        all-paths (trace-view/collect-all-paths tree)]
    (swap! *session assoc-in [:trace :expanded] all-paths)
    (render-trace-results request)))

(defn- trace-collapse-all
  "Collapses all nodes in the trace tree."
  [{:keys [*session] :as request}]
  (swap! *session assoc-in [:trace :expanded] #{})
  (render-trace-results request))

(defn- resolve-trace-source
  "Given the session and a node path string, resolves the trace node's source
   to a [file-path line resolved-path] triple, or nil if the node or source
   can't be found."
  [session node-path]
  (let [tree (get-in session [:trace :tree])
        node (when tree (trace/get-node tree node-path))
        [file-path line] (when node (trace/parse-source (:source node)))]
    (when file-path
      (let [project (-> session :process :project)
            root-dir (pf/root-dir project)
            resolved (fs/path root-dir file-path)]
        (when (fs/exists? resolved)
          [file-path line resolved])))))

(defn- read-source-lines
  "Reads a source file and returns a vector of text lines (trailing empty line stripped)."
  [resolved-path]
  (let [content (slurp (str resolved-path))
        raw-lines (string/split content #"\n" -1)]
    (if (and (> (count raw-lines) 1)
             (= "" (peek raw-lines)))
      (pop (vec raw-lines))
      (vec raw-lines))))

(defn- view-source
  "Serves a standalone HTML page displaying a Dialog source file with line numbers.
   The node path (e.g. \"0.2.1\") comes from path-params and identifies a node in
   the current trace tree. The node's :source field is parsed to get the file path
   and line number."
  [{:keys [*session path-params]}]
  (let [node-path (first path-params)]
    (if-let [[file-path line resolved] (resolve-trace-source @*session node-path)]
      (let [raw-lines (read-source-lines resolved)
            dg? (string/ends-with? file-path ".dg")
            lines (map-indexed
                   (fn [idx text]
                     (let [n (inc idx)]
                       {:number n
                        :text (if dg?
                                (syntax/highlight-line text)
                                (syntax/html-escape text))
                        :highlighted (= n line)}))
                   raw-lines)]
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (s/render-file "skein/source.html"
                              {:file-path file-path
                               :line line
                               :line-count (count raw-lines)
                               :lines lines})})
      {:status 404
       :headers {"Content-Type" "text/plain"}
       :body "Source not found"})))

(defn- source-preview
  "Returns an HTML fragment showing a small window of source code around the
   referenced line (4 lines of context above and below). Used for hover previews."
  [{:keys [*session path-params]}]
  (let [node-path (first path-params)]
    (if-let [[file-path line resolved] (resolve-trace-source @*session node-path)]
      (let [raw-lines (read-source-lines resolved)
            dg? (string/ends-with? file-path ".dg")
            context 4
            start (max 0 (- line context 1))
            end (min (count raw-lines) (+ line context))
            window (subvec raw-lines start end)
            lines (map-indexed
                   (fn [idx text]
                     (let [n (+ start idx 1)]
                       {:number n
                        :text (if dg?
                                (syntax/highlight-line text)
                                (syntax/html-escape text))
                        :highlighted (= n line)}))
                   window)]
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (s/render-file "skein/source-preview.html"
                              {:file-path file-path
                               :line line
                               :lines lines})})
      {:status 404
       :headers {"Content-Type" "text/plain"}
       :body "Source not found"})))

(defn- toggle-lock-knot
  "Toggles the locked state of the specified knot."
  [{:keys [*session] :as request}]
  (let [id (knot-id request)
        locked? (get-in @*session [:tree :knots id :locked])]
    (swap! *session session/toggle-lock id)
    (render-app request {:flash (if locked? "Unlocked" "Locked")})))

(defn- splice-out-knot
  "Splices out the specified knot, reparenting its children."
  [{:keys [*session] :as request}]
  (let [[error session'] (session/splice-out! @*session (knot-id request))]
    (reset! *session session')
    (if error
      (render-app request {:flash {:message error :type :error}})
      (render-app request {:flash "Spliced out"}))))

(defn- jump-to-status
  "Jumps to the next knot with the given status, cycling through matches."
  [{:keys [*session] :as request}]
  (let [status (-> request :path-params first keyword)
        session @*session
        tree (:tree session)
        matching (tree/knots-with-status tree status)]
    (when (seq matching)
      (let [last-id (get-in session [:last-jump status])
            idx (when last-id
                  (let [i (.indexOf matching last-id)]
                    (when (>= i 0) i)))
            next-idx (if idx
                       (mod (inc idx) (count matching))
                       0)
            next-id (nth matching next-idx)]
        (swap! *session assoc-in [:last-jump status] next-id)
        (swap! *session session/select-knot next-id)
        (render-app request {:scroll-to-knot-id next-id})))))

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
     :body (html [:<>
                  [:div#app] ; Clear the main body of the app
                  [modals/close-window]])}))

(defn- open-quit
  "Checks if session is dirty. If so, shows quit confirmation modal.
   If not dirty, proceeds directly to quit."
  [{:keys [*session] :as request}]
  (let [session @*session
        dirty? (:dirty? session)]
    (if dirty?
      ;; Show confirmation modal
      {:status 200
       :body (html [modals/quit-modal])}
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
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (s/render-file "skein/index.html"
                          {:dev development-mode?
                           :version (env/version)})}))

(def ^:private routes
  (router/routes
   "GET /" req
   (render-index req)

   "POST /action/new-command/*" req
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

   "POST /action/toggle-lock/*" req
   (toggle-lock-knot req)

   "POST /action/splice-out/*" req
   (splice-out-knot req)

   "GET /action/jump-to-status/*" req
   (jump-to-status req)

   "GET /action/quit" req
   (open-quit req)

   "POST /action/save-and-quit" req
   (save-and-quit req)

   "POST /action/quit-without-saving" req
   (quit-without-saving req)

   "GET /app" req
   (render-app req)

   "GET /fab" req
   {:status 200
    :body (html (ui.app/render-fab @(:*session req)))}

   "GET /action/dynamic/*" req
   (show-dynamic-state req)

   "POST /action/trace/toggle/*" req
   (trace-toggle req)

   "POST /action/trace/search" req
   (trace-search req)

   "POST /action/trace/expand-all" req
   (trace-expand-all req)

   "POST /action/trace/collapse-all" req
   (trace-collapse-all req)

   "POST /action/trace/*" req
   (trace-knot req)

   "GET /action/source-preview/*" req
   (source-preview req)

   "GET /action/source/**" req
   (view-source req)

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
