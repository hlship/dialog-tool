(ns playground
  (:require [dialog-tool.project-file :as pf]))


(comment
  (-> (pf/read-project "../../olivia/petshop")
      (pf/expand-sources {:debug true}))

  )