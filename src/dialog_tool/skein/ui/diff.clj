(ns dialog-tool.skein.ui.diff)

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

(defn compute-diff
  "Compute word-level diff between old-text and new-text.
   Returns a sequence of {:type :added/:removed/:unchanged :value string} maps."
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
    (let [old-tokens (tokenize old-text)
          new-tokens (tokenize new-text)
          matrix (lcs-matrix old-tokens new-tokens)]
      (backtrack-lcs matrix old-tokens new-tokens))))

