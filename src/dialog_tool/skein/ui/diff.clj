(ns dialog-tool.skein.ui.diff)

(def ^:private *cache (atom {}))

(defn clear-cache
  []
  (swap! *cache empty))

;; Word-diff implementation using longest common subsequence algorithm

(defn- tokenize
  "Split text into tokens of words and whitespace, preserving both."
  [s]
  (when s
    (re-seq #"\s+|\S+" s)))

(defn- lcs-matrix
  "Build a matrix for longest common subsequence of two sequences."
  [xs ys]
  (let [m (count xs)
        n (count ys)
        ;; Create a 2D vector initialized with zeros
        matrix (vec (repeat (inc m) (vec (repeat (inc n) 0))))]
    (reduce
     (fn [mat i]
       (reduce
        (fn [mat' j]
          (if (= (nth xs (dec i)) (nth ys (dec j)))
            (assoc-in mat' [i j] (inc (get-in mat' [(dec i) (dec j)])))
            (assoc-in mat' [i j] (max (get-in mat' [(dec i) j])
                                      (get-in mat' [i (dec j)])))))
        mat
        (range 1 (inc n))))
     matrix
     (range 1 (inc m)))))

(defn- backtrack-lcs
  "Backtrack through the LCS matrix to produce diff operations."
  [matrix xs ys]
  (loop [i (count xs)
         j (count ys)
         result []]
    (cond
      (and (zero? i) (zero? j))
      (reverse result)

      (zero? i)
      (recur i (dec j) (conj result {:type :added :value (nth ys (dec j))}))

      (zero? j)
      (recur (dec i) j (conj result {:type :removed :value (nth xs (dec i))}))

      (= (nth xs (dec i)) (nth ys (dec j)))
      (recur (dec i) (dec j) (conj result {:type :unchanged :value (nth xs (dec i))}))

      (> (get-in matrix [(dec i) j]) (get-in matrix [i (dec j)]))
      (recur (dec i) j (conj result {:type :removed :value (nth xs (dec i))}))

      :else
      (recur i (dec j) (conj result {:type :added :value (nth ys (dec j))})))))

(defn- coalesce-diff
  "Merge consecutive diff entries of the same type into single blocks.
   Reduces the number of spans in the output."
  [diff-seq]
  (when (seq diff-seq)
    (loop [[current & remaining] diff-seq
           result []
           acc nil]
      (cond
        ;; No more items - flush accumulator and return
        (nil? current)
        (if acc
          (conj result acc)
          result)

        ;; No accumulator yet - start one
        (nil? acc)
        (recur remaining result current)

        ;; Same type - merge values
        (= (:type acc) (:type current))
        (recur remaining result (update acc :value str (:value current)))

        ;; Different type - flush accumulator and start new one
        :else
        (recur remaining (conj result acc) current)))))

(defn- compute-diff*
  [old-text new-text]
  (let [old-tokens (tokenize old-text)
        new-tokens (tokenize new-text)
        matrix     (lcs-matrix old-tokens new-tokens)
        raw-diff   (backtrack-lcs matrix old-tokens new-tokens)]
    (coalesce-diff raw-diff)))

(defn- compute-diff
  [old-text new-text]
  (let [k      [old-text new-text]
        cached (get @*cache k)]
    ;; This is good enough for effectively single-threaded execution
    (or cached
        (let [result (compute-diff* old-text new-text)]
          (swap! *cache assoc k result)
          result))))

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
