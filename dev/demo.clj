(ns demo
  "Used when manually testing the skein."
  (:require [dialog-tool.env :as env]
            [dialog-tool.skein.service :as service :refer [stop! *app]]
            [dialog-tool.skein.session :as session]
            [dialog-tool.skein.tree :as tree]))

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
  (-> @*app :global :session :tree :active-knot-id)
  (-> @*app :global :session session/selected-knots)
  (alter-var-root #'env/*debug* (constantly false))
  env/*debug*

  (swap! *app assoc-in [:global :modal] nil)

  (stop!)

  (start! "../sanddancer-dialog" nil)

  (start! "../futurama" nil)

  (start! "../sanddancer-dialog" (str "/tmp/" (random-uuid) ".skein") nil)

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
