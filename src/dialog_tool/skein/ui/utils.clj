(ns dialog-tool.skein.ui.utils
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [starfederation.datastar.clojure.adapter.http-kit :as hk-gen]
            [starfederation.datastar.clojure.api :as d*]
            [clojure.string :as string]))

(defn patch-elements!
  [*session markup]
  (d*/patch-elements! (:sse-gen @*session)
                      ;; In likely case it's a Huff RawString
                      (str markup)))

(defn patch-signals!
  [*session signals]
  (d*/patch-signals! (:sse-gen @*session)
                     (json/generate-string signals)))

(defn start-sse
  [{:keys [*session] :as request} on-open-fn]
  (hk-gen/->sse-response request
                         {hk-gen/on-open
                          (fn [sse-gen]
                            (swap! *session assoc :sse-gen sse-gen)
                            (on-open-fn sse-gen))}))

(defn classes
  "Combines multiple class strings into a single string, skipping nils, and reducing spaces to a single space.
   "
  [& s]
  (-> (string/join " " s)
      (string/replace #"\s+" " ")
      string/trim))

(defn wrap-parse-signals
  "Middleware that parses Datastar signals and adds them to the request as :signals."
  [handler]
  (fn [request]
    (let [data (d*/get-signals request)
          signals (cond
                    (string? data)
                    (json/parse-string data true)
                    
                    (nil? data)
                    nil
                    
                    :else
                    (with-open [r (io/reader data)]
                      (json/parse-stream r true)))]
      (handler (assoc request :signals signals)))))
