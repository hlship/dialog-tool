(ns demo
  "Used when manually testing the skein."
  (:require [dialog-tool.skein.service :as service :refer [stop! *app]]))

(-> @*app :global :modal)

(defn- start!
  ([path opts]
   (start! path nil opts))
  ([path skein-file opts]
   (stop!)
   (service/start! path
                   (merge {:port                10140
                           :skein-path          (str path "/" (or skein-file "default.skein"))
                           :exit-when-shutdown? false}
                          opts))))

(comment
  (-> @*app :global :session :tree :knots (get 1728874698428))
  (-> @*app :tab keys)
  (-> @*app :global :modal)
  (-> @*app :global :shutdown-fn)

  (swap! *app assoc-in [:global :modal] nil)

  (stop!)

  (start! "../sanddancer-dialog" nil)

  (start! "../futurama" nil)

  (start! "../futurama" "/tmp/fut.skein" nil)

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
