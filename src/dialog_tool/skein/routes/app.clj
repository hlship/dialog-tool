(ns dialog-tool.skein.routes.app
  (:require [clj-simple-router.core :as router]
            [clojure.string :as string]
            [dialog-tool.skein.tree :as tree]
            [huff2.core :refer [html]]))

;; Recreating a number of things supplied by the flowbrite-svelte library

(defn svg [label & body]
  [:svg {:xmlns      "https://www.w3.org/2000/svg"
         :fill       "currentColor"
         :class      "shrink-0 w-5 h-5 me-2"
         :aria-label label
         :view-box   "0 0 24 24"}
   body])

(defn icon-play
  []
  [svg "play solid"
   [:path
    {:fill-rule "evenodd"
     :d         "M8.6 5.2A1 1 0 0 0 7 6v12a1 1 0 0 0 1.6.8l8-6a1 1 0 0 0 0-1.6l-8-6Z"
     :clip-rule "evenodd"}]])

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

(defn icon-floppy-disk
  []
  [svg "floppy disk alt solid"
   [:path {:fill-rule "evenodd"
           :d         "M5 3a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V7.414A2 2 0 0 0 20.414 6L18 3.586A2 2 0 0 0 16.586 3H5Zm3 11a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v6H8v-6Zm1-7V5h6v2a1 1 0 0 1-1 1h-4a1 1 0 0 1-1-1Z"
           :clip-rule "evenodd"}]
   [:path {:fill-rule "evenodd"
           :d         "M14 17h-4v-2h4v2Z"
           :clip-rule "evenodd"}]])

(defn navbar
  [title tree]
  (let [{:keys [new error ok]} (tree/counts tree)]
    [:nav {:class        (trim "bg-white text-gray-500 border-gray-200 divide-gray-200"
                               "px-2 sm:px-4 py-2.5"
                               "fixed w-full z-20 top-0 start-0 border-b")
           :data-signals "{dirty: false}"}
     [:div.mx-auto.flex.flex-wrap.justify-between.items-center.container
      [:a.flex.items-center
       [:div.self-center.whitespace-nowrap.text-xl.font-semibold
        title]]
      [:div.mx-0.inline-flex
       [:div.text-black.bg-green-400.p-2.font-semibold.rounded-l-lg ok]
       [:div.text-black.bg-yellow-200.p-2.font-semibold new]
       [:div.text-black.bg-red-500.p-2.font-semibold.rounded-r-lg error]
       [nav-button nil "Jump"]
       [:div.flex.md:order-2.space-x-2
        [nav-button {:data-on:click "@get('/action/replay-all')"} [:<> [icon-play] "Replay All"]]
        [nav-button {:class      button-base
                     :data-class "{'bg-blue-700': $dirty, 'hover:bg-blue-800': $dirty, 'bg-green-700': !$dirty, 'hover:bg-green-800': !$dirty}"}
         [:<> [icon-floppy-disk] "Save"]]
        [nav-button nil "Undo"]
        [nav-button nil "Redo"]
        [nav-button nil "Quit"]]]]]))

(defn render-knot
  [{:keys [id response]}]
  [:div.border-x-4.border-slate-100 {:id id}                ;; TODO: Color by category
   [:div.bg-yellow-50.w-full.whitespace-pre.relative.p-2
    ;; TODO: All the controls                                                      
    ;; TODO: Show the diff when unblessed not nil
    response]
   [:hr]])

(defn app
  [{:keys [*session]}]
  (let [session @*session
        {:keys [skein-path tree]} session]
    (html
      [:div#app.relative.px-8
       [navbar skein-path tree]
       [:div.container.mx-lg.mx-auto.mt-16
        (map render-knot (tree/selected-knots tree))]])))

(def routes
  (router/routes
    "GET /app" req
    {:status 200
     :body   (app req)}))
