(ns demo
  "Used when manually testing the skein."
  (:require [dialog-tool.skein.service :as service :refer [stop! *app]]))

(-> @*app :global :session :modal)

(defn- start!
  ([path opts]
   (start! path nil opts))
  ([path skein-file opts]
   (stop!)
   (service/start! path
                   (assoc opts
                          :port 10140
                          :skein-path (str path "/" (or skein-file "default.skein"))
                          :exit-when-shutdown? false))))

(comment

  (stop!)

  (start! "../sanddancer-dialog" nil)


  (start! "../failure" nil)

  (start! "../dialog-extensions/tree"
          {:development-mode? true})

  (start! "../dialog-extensions/who" nil)

  (start! "../dialog-extensions/who" "frotz.skein"
          {:seed              10101
           :development-mode? true
           :engine            :frotz})

  ;;
  )
