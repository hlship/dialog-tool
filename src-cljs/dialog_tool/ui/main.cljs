(ns dialog-tool.ui.main
  (:require
   ;; Import for side effects - registers web components (el-dropdown, el-menu, etc.)
   ["@tailwindplus/elements"]
   ;; Import for side effects - initializes Datastar data-* attribute handling
   ["/vendor/datastar.js" :refer [attribute]]))

(defn- get-margin-bottom
  "Returns the computed margin-bottom of an element in pixels."
  [el]
  (-> js/window
      (.getComputedStyle el)
      (.-marginBottom)
      js/parseFloat))

(defn- register-scroll-into-view-attribute
  "Registers the data-scroll-into-view attribute with Datastar.
   When set to true, the element will be scrolled into view using smooth scrolling
   with block: 'end' to ensure the entire element is visible, plus additional
   scrolling based on the element's margin-bottom for visual breathing room.
   Also focuses the first input element within the scrolled element."
  []
  (attribute
   #js {:name "scroll-into-view"
        :requirement #js {:key "denied" :value "must"}
        :returnsValue true
        :apply (fn [ctx]
                 (let [el (.-el ctx)
                       rx (.-rx ctx)
                       check-and-scroll (fn []
                                          (when (rx)
                                            (.scrollIntoView el #js {:behavior "smooth"
                                                                     :block "end"})
                                            ;; Add extra scroll based on element's margin-bottom
                                            (let [margin-bottom (get-margin-bottom el)]
                                              (when (pos? margin-bottom)
                                                (js/setTimeout
                                                 #(.scrollBy js/window #js {:top margin-bottom
                                                                            :behavior "smooth"})
                                                 300)))
                                            (when-let [input (.querySelector el "input")]
                                              (.focus input))))]
                   (check-and-scroll)
                   ;; Return cleanup function
                   (fn [])))}))

(defn init
  "Called once when the app loads."
  []
  (register-scroll-into-view-attribute)
  (js/console.log "ClojureScript initialized"))

(defn refresh
  "Called by shadow-cljs after hot-reload."
  []
  (js/console.log "Hot reload complete"))

