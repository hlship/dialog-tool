(ns dialog-tool.skein.ui.diff
  (:require [plumula.diff :as d]))

(defn- remap
  [{::d/keys [operation text]}]
  {:type  (get {::d/insert :added
                ::d/delete :removed
                ::d/equal  :unchanged}
               operation)
   :value text})

(defn- compute-diff
  [old-text new-text]
  (->> (d/diff old-text new-text ::d/cleanup ::d/cleanup-semantic)
       (mapv remap)))

(defn diff-text
  "Compute word-level diff between old-text and new-text.
   Returns a sequence of coalesced blocks: {:type :added/:removed/:unchanged :value string} maps.
   Consecutive tokens of the same type are merged to reduce the number of output spans."
  [old-text new-text]
  (cond
    ;; No response yet, everything in unblessed is new
    (nil? old-text)
    [{:type :added :value new-text}]

    ;; Unblessed is nil, just show response unchanged
    (nil? new-text)
    [{:type :unchanged :value old-text}]

    ;; Both present, compute word-level diff
    :else
    (compute-diff old-text new-text)))
