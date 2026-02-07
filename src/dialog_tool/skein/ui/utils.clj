(ns dialog-tool.skein.ui.utils
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [huff2.core :refer [html]]
            [starfederation.datastar.clojure.adapter.http-kit2 :as hk-gen]
            [starfederation.datastar.clojure.api :as d*]
            [clojure.string :as string]))

(defn patch-elements!
  [sse-gen markup]
  (d*/patch-elements! sse-gen
                      (if
                        (vector? markup)
                        (-> markup html str)
                        ;; In likely case it's a Huff RawString
                        (str markup))))

(defn patch-signals!
  [sse-gen signals]
  (d*/patch-signals! sse-gen
                     (json/generate-string signals)))

(defn with-sse
  "Runs operation-fn with SSE open and operating, returning a response map.
  
  The operation-fn is passed the sse-gen object so that it use the Datastar API functions
  (in starfederation.datastar.clojure.api)."
  [request operation-fn]
  ;; NOTE: my earlier attempt to lazily create the sse-gen and store it in the *session failed
  ;; on the 2nd and subsequent requests.  Strangely, when my service shutdown, I saw a flash of behavior
  ;; in my webpage ... was this just a caching issue?  A missing flush?  Or was it the ReentrantLock?
  (hk-gen/->sse-response request
                         {hk-gen/on-open
                          (fn [sse-gen]
                            (operation-fn sse-gen))}))

(defn with-short-sse
  "Runs the operation-fn with SSE, but closes the SSE after the function is invoked."
  [request operation-fn]
  (with-sse request
            (fn [sse-gen]
              (operation-fn sse-gen)
              (d*/close-sse! sse-gen))))


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
    (let [data    (d*/get-signals request)
          signals (cond
                    (string? data)
                    (json/parse-string data true)

                    (nil? data)
                    nil

                    :else
                    (with-open [r (io/reader data)]
                      (json/parse-stream r true)))]
      (handler (assoc request :signals signals)))))
