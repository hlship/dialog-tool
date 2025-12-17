(ns dialog-tool.skein.ui.app
  (:require [clojure.string :as string]
            [dialog-tool.skein.ui.svg :as svg]
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
  (let [old-tokens (tokenize old-text)
        new-tokens (tokenize new-text)]
    (cond
      (and (seq old-tokens) (seq new-tokens))
      (let [matrix (lcs-matrix old-tokens new-tokens)]
        (backtrack-lcs matrix old-tokens new-tokens))

      ;; No response yet, everything in unblessed is new
      (nil? old-text)
      [{:type :added :value new-text}]

      ;; Unblessed is nil, just show response unchanged
      :else
      [{:type :unchanged :value old-text}])))

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

(defn- trim
  [& s]
  (-> (string/join " " s)
      (string/replace #"\s+" " ")))

(def button-base
  (trim "text-center font-medium"
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
    [:nav {:class        (trim "bg-white text-gray-500 border-gray-200 divide-gray-200"
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
  [{:keys [id response unblessed] :as knot}]
  (let [category (tree/assess-knot knot)
        border-class (category->border-class category)]
    [:div.border-x-4 {:id    (str "knot-" id)
                      :class border-class}
   [:div.bg-yellow-50.w-full.whitespace-pre.relative.p-2
    [:div.whitespace-normal.flex.flex-row.absolute.top-2.right-2.gap-x-2
     [dropdown/dropdown {:post-url "/actions/color"
                         :label [svg/dots-vertical]}
      [dropdown/button nil "Run from start to here"]
      [dropdown/button nil "Delete"]
      [dropdown/button nil "Splice Out"]
      [dropdown/button nil "Bless"]
      [dropdown/button nil "Bless To Here"]
      [dropdown/button nil "Edit Label"]
      [dropdown/button nil "Edit Command"]
      [dropdown/button nil "Insert Parent"]
      [dropdown/button nil "New Child"]
      [dropdown/button nil "Replay"]]]
    [render-diff response unblessed]]
     [:hr]]))

(defn render-app
  [request]
  (let [{:keys [*session]} request
        session @*session
        {:keys [skein-path tree]} session
        selected-knots (tree/selected-knots tree)]
    [:div#app.relative.px-8
     [navbar skein-path tree]
     [:div.container.mx-lg.mx-auto.mt-16
      (map render-knot selected-knots)
      [new-command/new-command-input] 
      [:div
       "Signals:"
       [:br]
       [:pre {:data-json-signals true}] ] ]]))
