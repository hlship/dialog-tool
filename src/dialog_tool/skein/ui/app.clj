(ns dialog-tool.skein.ui.app
  (:require [cheshire.core :as json]
            [clojure.string :as string]
            [dialog-tool.skein.ui.svg :as svg]
            [dialog-tool.skein.ui.components :as c]
            [dialog-tool.skein.tree :as tree]))


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
  [:nav {:class        (trim "bg-white text-gray-500 border-gray-200 divide-gray-200"
                             "px-2 sm:px-4 py-2.5"
                             "fixed w-full z-20 top-0 start-0 border-b")
         :data-signals (json/generate-string
                         {:dirty  false
                          :counts (tree/counts tree)})}
   [:div.mx-auto.flex.flex-wrap.justify-between.items-center.container
    [:a.flex.items-center
     [:div.self-center.whitespace-nowrap.text-xl.font-semibold
      title]]
    [:div.mx-0.inline-flex
     [:div.text-black.bg-green-400.p-2.font-semibold.rounded-l-lg {:data-text :$counts.ok}]
     [:div.text-black.bg-yellow-200.p-2.font-semibold {:data-text :$counts.new}]
     [:div.text-black.bg-red-500.p-2.font-semibold.rounded-r-lg {:data-text :$counts.error}]
     [nav-button nil "Jump"]
     [:div.flex.md:order-2.space-x-2
      [nav-button {:data-on:click "@get('/action/replay-all')"} [:<> [svg/icon-play] "Replay All"]]
      [nav-button {:class      button-base
                   :data-class "{'bg-blue-700': $dirty, 'hover:bg-blue-800': $dirty, 'bg-green-700': !$dirty, 'hover:bg-green-800': !$dirty}"}
       [:<> [svg/icon-floppy-disk] "Save"]]
      [nav-button nil "Undo"]
      [nav-button nil "Redo"]
      [nav-button nil "Quit"]]]]])

(defn render-knot
  [{:keys [id response]}]
  [:div.border-x-4.border-slate-100 {:id (str "knot-" id)}  ;; TODO: Color by category
   [:div.bg-yellow-50.w-full.whitespace-pre.relative.p-2
    [:div.whitespace-normal.flex.flex-row.absolute.top-2.right-2.gap-x-2
     [c/dropdown {:options  ["Red" "Green" "Blue"]
                  :post-url "/actions/color"}]]
    
    ;; TODO: All the controls                                                      
    ;; TODO: Show the diff when unblessed not nil
    response]
   [:hr]])

(defn render-app
  [request]
  (let [{:keys [*session]} request
        session @*session
        {:keys [skein-path tree]} session]
    [:div#app.relative.px-8
     [navbar skein-path tree]
     [:div.container.mx-lg.mx-auto.mt-16
      (map render-knot (tree/selected-knots tree))]]))
