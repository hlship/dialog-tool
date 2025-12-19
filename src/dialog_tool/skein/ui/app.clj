(ns dialog-tool.skein.ui.app
  (:require [dialog-tool.skein.ui.svg :as svg]
            [dialog-tool.skein.ui.utils :refer [classes]]
            [dialog-tool.skein.ui.components.dropdown :as dropdown]
            [dialog-tool.skein.ui.components.new-command :as new-command]
            [dialog-tool.skein.tree :as tree]))

;; Word-diff implementation using longest common subsequence algorithm

(defn- tokenize
  "Split text into tokens of words and whitespace, preserving both."
  [s]
  (when s
    (re-seq #"\s+|\S+" s)))

(defn- lcs-matrix
  "Build a matrix for longest common subsequence of two sequences."
  [xs ys]
  (let [m (count xs)
        n (count ys)
        ;; Create a 2D vector initialized with zeros
        matrix (vec (repeat (inc m) (vec (repeat (inc n) 0))))]
    (reduce
     (fn [mat i]
       (reduce
        (fn [mat' j]
          (if (= (nth xs (dec i)) (nth ys (dec j)))
            (assoc-in mat' [i j] (inc (get-in mat' [(dec i) (dec j)])))
            (assoc-in mat' [i j] (max (get-in mat' [(dec i) j])
                                      (get-in mat' [i (dec j)])))))
        mat
        (range 1 (inc n))))
     matrix
     (range 1 (inc m)))))

(defn- backtrack-lcs
  "Backtrack through the LCS matrix to produce diff operations."
  [matrix xs ys]
  (loop [i (count xs)
         j (count ys)
         result []]
    (cond
      (and (zero? i) (zero? j))
      (reverse result)

      (zero? i)
      (recur i (dec j) (conj result {:type :added :value (nth ys (dec j))}))

      (zero? j)
      (recur (dec i) j (conj result {:type :removed :value (nth xs (dec i))}))

      (= (nth xs (dec i)) (nth ys (dec j)))
      (recur (dec i) (dec j) (conj result {:type :unchanged :value (nth xs (dec i))}))

      (> (get-in matrix [(dec i) j]) (get-in matrix [i (dec j)]))
      (recur (dec i) j (conj result {:type :removed :value (nth xs (dec i))}))

      :else
      (recur i (dec j) (conj result {:type :added :value (nth ys (dec j))})))))

(defn- compute-diff
  "Compute word-level diff between old-text and new-text.
   Returns a sequence of {:type :added/:removed/:unchanged :value string} maps."
  [old-text new-text]
  (cond
    ;; No response yet, everything in unblessed is new
    (nil? old-text)
    [{:type :added :value new-text}]

    ;; Unblessed is nil, just show response unchanged
    (nil? new-text)
    [{:type :unchanged :value old-text}]

    ;; Both present, compute word-level diff
    :else
    (let [old-tokens (tokenize old-text)
          new-tokens (tokenize new-text)
          matrix (lcs-matrix old-tokens new-tokens)]
      (backtrack-lcs matrix old-tokens new-tokens))))

(defn render-diff
  "Render the difference between response and unblessed as hiccup markup.
   When unblessed is nil, just renders the response as-is.
   When unblessed is present, shows word-level diff:
   - Removed text (in response but not unblessed): red strikethrough
   - Added text (in unblessed but not response): blue bold
   - Unchanged text: normal styling"
  [response unblessed]
  (if unblessed
    (let [changes (compute-diff response unblessed)]
      (into [:<>]
            (map (fn [{:keys [type value]}]
                   (case type
                     :added [:span.text-blue-700.font-bold value]
                     :removed [:span.text-red-800.font-bold.line-through value]
                     :unchanged value)))
            changes))
    ;; No unblessed, just show response as-is
    response))

(def button-base
  (classes "text-center font-medium"
           "focus-within:ring-4 focus-within:outline-none"
           "inline-flex items-center"
           "justify-center px-3 py-2 text-xs text-white"
           "rounded-lg ml-8"))

(def blue-button
  (str button-base " bg-blue-700 hover:bg-blue-800"))

(defn nav-button [attrs body]
  [:button (merge {:type  "button"
                   :class blue-button}
                  attrs)
   body])

(defn navbar
  [title tree]
  (let [{:keys [ok new error]} (tree/counts tree)]
    [:nav {:class        (classes "bg-white text-gray-500 border-gray-200 divide-gray-200"
                                  "px-2 sm:px-4 py-2.5"
                                  "fixed w-full z-20 top-0 start-0 border-b")
           :data-signals:dirty "false"}
     [:div.mx-auto.flex.flex-wrap.justify-between.items-center.container
      [:a.flex.items-center
       [:div.self-center.whitespace-nowrap.text-xl.font-semibold
        title]]
      [:div.mx-0.inline-flex
       [:div.text-black.bg-green-400.p-2.font-semibold.rounded-l-lg  ok]
       [:div.text-black.bg-yellow-200.p-2.font-semibold new]
       [:div.text-black.bg-red-500.p-2.font-semibold.rounded-r-lg error]
       [nav-button nil "Jump"]
       [:div.flex.md:order-2.space-x-2
        [nav-button {:data-on:click "@get('/action/replay-all')"} [:<> [svg/icon-play] "Replay All"]]
        [nav-button {:class      button-base
                     :data-class "{'bg-blue-700': $dirty, 'hover:bg-blue-800': $dirty, 'bg-green-700': !$dirty, 'hover:bg-green-800': !$dirty}"}
         [:<> [svg/icon-floppy-disk] "Save"]]
        [nav-button nil "Undo"]
        [nav-button nil "Redo"]
        [nav-button nil "Quit"]]]]]))

(def ^:private category->border-class
  {:ok    "border-slate-100"
   :new   "border-yellow-200"
   :error "border-rose-400"})

(defn render-knot
  [{:keys [id label response unblessed] :as knot} enable-bless-to?]
  (let [category (tree/assess-knot knot)
        border-class (category->border-class category)]
    [:div.border-x-4 {:id    (str "knot-" id)
                      :class border-class}
     [:div.bg-yellow-50.w-full.whitespace-pre.relative.p-2
      [:div.whitespace-normal.flex.flex-row.absolute.top-2.right-2.gap-x-2
       (when label
         [:span.font-bold.bg-gray-200.p-1.rounded-md label])
       [dropdown/dropdown {:id (str "actions-" id)
                           :label [svg/dots-vertical]}
        [dropdown/button nil "Replay" "Run from start to here"]
        (when (not= 0 id)
          [:<>
           [dropdown/button {:disabled (not enable-bless-to?)
                             :data-on:click (str "@post('/action/bless-to/" id "')")}
            "Bless To Here" "Accept changes from root to here"]
           [dropdown/button nil "Insert Parent" "Insert a command before this"]
           [dropdown/button nil "Delete" "Delete this knot and all children"]
           [dropdown/button nil "Splice Out" "Delete this knot, reparent childen up"]
           [dropdown/button nil "Edit Command" "Change the knot's command"]])
        [dropdown/button {:disabled      (= category :ok)
                          :data-on:click (str "@post('/action/bless/" id "')")}
         "Bless" "Accept changes"]
        (when (not= 0 id)
          [:<>
           [dropdown/button nil "Edit Label" "Change label for knot"]
           [dropdown/button {:disabled (not enable-bless-to?)
                             :data-on:click (str "@post('/action/bless-to/" id "')")}
            "Bless To Here" "Accept changes from root to here"]
           [dropdown/button nil "Edit Command" "Change the knot's command"]
           [dropdown/button nil "Insert Parent" "Insert a command before this"]])
        [dropdown/button nil "New Child" "Add a new command after this"]]]
      [render-diff response unblessed]]
     [:hr]]))

(defn- compute-bless-to-flags
  "Returns a seq of [knot, enable-bless-to?] pairs. enable-bless-to? is true if any knot
   from root to this knot (inclusive) is not :ok."
  [knots]
  (second
   (reduce (fn [[any-not-ok? result] knot]
             (let [not-ok? (not= :ok (tree/assess-knot knot))
                   any-not-ok?' (or any-not-ok? not-ok?)]
               [any-not-ok?' (conj result [knot any-not-ok?'])]))
           [false []]
           knots)))

(defn render-app
  [request]
  (let [{:keys [*session]} request
        session @*session
        {:keys [skein-path tree]} session
        knots-with-flags (-> tree tree/selected-knots compute-bless-to-flags)]
    [:div#app.relative.px-8
     [navbar skein-path tree]
     [:div.container.mx-lg.mx-auto.mt-16
      (map (fn [[knot enable-bless-to?]] (render-knot knot enable-bless-to?)) knots-with-flags)
      [new-command/new-command-input]
      [:div.fixed.top-4.left-4.bg-gray-800.text-white.p-3.rounded-lg.shadow-lg.max-w-md.max-h-64.overflow-auto.z-50.text-xs
       [:pre.whitespace-pre-wrap {:data-json-signals true}]]]]))
