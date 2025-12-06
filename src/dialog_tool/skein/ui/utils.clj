(ns dialog-tool.skein.ui.utils
  (:require [cheshire.core :as json]
            [dialog-tool.skein.tree :as tree]
            [starfederation.datastar.clojure.adapter.http-kit :as hk-gen]
            [starfederation.datastar.clojure.api :as d*]))

(defn patch-elements!
  [*session markup]
  (d*/patch-elements! (:sse-gen @*session)
                      ;; In likely case it's a Huff RawString
                      (str markup)))

(defn patch-signals!
  [*session signals]
  (d*/patch-signals! (:sse-gen @*session)
                     (json/generate-string signals)))

(def *id (atom 0))

(defn unique-id
  [prefix]
  (str prefix (swap! *id inc)))

(defn start-sse
  [{:keys [*session] :as request} on-open-fn]
  (hk-gen/->sse-response request
                         {hk-gen/on-open
                          (fn [sse-gen]
                            (swap! *session assoc :sse-gen sse-gen)
                            (on-open-fn sse-gen))}))
