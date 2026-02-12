(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'net.lewisship/dialog-tool)
(def version "0.0.1")
(def class-dir "target/classes")
(def uber-file (format "target/%s-%s.jar" (name lib) version))

(defn uber [_]
      (b/copy-dir {:src-dirs ["src" "resources"]
                   :target-dir class-dir})
      (b/compile-clj {:basis (b/create-basis {:project "deps.edn"})
                      :class-dir class-dir})
      (b/uber {:class-dir class-dir
               :uber-file uber-file
               :basis (b/create-basis {:project "deps.edn"})
               :main 'dialog-tool.main}))
