(ns dialog-tool.skein.ui.svg)

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


(defn icon-floppy-disk
  []
  [svg "floppy disk alt solid"
   [:path {:fill-rule "evenodd"
           :d         "M5 3a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V7.414A2 2 0 0 0 20.414 6L18 3.586A2 2 0 0 0 16.586 3H5Zm3 11a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v6H8v-6Zm1-7V5h6v2a1 1 0 0 1-1 1h-4a1 1 0 0 1-1-1Z"
           :clip-rule "evenodd"}]
   [:path {:fill-rule "evenodd"
           :d         "M14 17h-4v-2h4v2Z"
           :clip-rule "evenodd"}]])

(defn dots-vertical
  []
  [svg{:label "dots vertical outline" 
       :class "w-4 h-4"}
   [:path
    {:stroke "currentColor"
     :stroke-linecap "round"
     :stroke-width "3"
     :d "M12 6h.01M12 12h.01M12 18h.01"}]])