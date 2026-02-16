(ns build
  (:require [clojure.tools.build.api :as b]))

(defn uber
  [params]
  (let [{:keys [uber-file class-dir]
         :or   {uber-file "out/build/dialog-tool-DEV.jar"
                class-dir "out/classes"}} params
        basis (b/create-basis {:project "deps.edn"
                               :aliases [:clojure]})]
    (b/copy-dir {:src-dirs   ["src"
                              "resources"]
                 :target-dir class-dir})
    (b/compile-clj {:basis      basis
                    :ns-compile '[dialog-tool.skein.main
                                  dialog-tool.commands
                                  dialog-tool.main]
                    :class-dir  class-dir})
    (b/uber {:class-dir class-dir
             :uber-file uber-file
             :basis     basis
             :main      'dialog-tool.main})))
