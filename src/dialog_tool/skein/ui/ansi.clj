(ns dialog-tool.skein.ui.ansi
  "Converts ANSI SGR escape codes to either styled Hiccup markup (for blessed output)
   or visible pseudo-markers (for diff output where styling is overridden by diff spans)."
  (:require [clojure.string :as string]))

(def ^:private sgr-pattern
  "Matches ANSI CSI SGR sequences: ESC [ <params> m"
  #"\u001b\[([0-9;]*)m")

(def ^:private color-names
  "Map from SGR color code to color name."
  {30 "black"
   31 "red"
   32 "green"
   33 "yellow"
   34 "blue"
   35 "magenta"
   36 "cyan"
   37 "white"})

(def ^:private color-codes
  "Set of SGR codes that represent foreground colors."
  (set (keys color-names)))

(defn- parse-sgr
  "Parses the parameter string from a CSI sequence into a seq of integers.
   An empty string is treated as [0] (reset)."
  [params-str]
  (if (string/blank? params-str)
    [0]
    (mapv #(parse-long %) (string/split params-str #";"))))

(defn- parse-segments
  "Splits text into segments of [:text string] and [:sgr params-vec].
   Returns a vector of segments."
  [text]
  (let [matcher (re-matcher sgr-pattern text)]
    (loop [pos 0
           segments []]
      (if (.find matcher)
        (let [match-start (.start matcher)
              match-end (.end matcher)
              params (parse-sgr (.group matcher 1))
              segments (if (> match-start pos)
                         (conj segments [:text (subs text pos match-start)])
                         segments)]
          (recur match-end (conj segments [:sgr params])))
        ;; No more matches; emit trailing text
        (if (< pos (count text))
          (conj segments [:text (subs text pos)])
          segments)))))

(defn- apply-sgr
  "Applies SGR codes to the current effects map.
   Effects map: {:bold bool, :italic bool, :color \"name\" or nil}"
  [effects codes]
  (reduce (fn [eff code]
            (cond
              (= code 0) {}
              (= code 1) (assoc eff :bold true)
              (= code 3) (assoc eff :italic true)
              (color-codes code) (assoc eff :color (color-names code))
              :else eff))
          effects
          codes))

(defn- effects->css-classes
  "Converts an effects map to a CSS class string."
  [{:keys [bold italic color]}]
  (let [classes (cond-> []
                  bold (conj "ansi-bold")
                  italic (conj "ansi-italic")
                  color (conj (str "ansi-" color)))]
    (when (seq classes)
      (string/join " " classes))))

(defn ansi->hiccup
  "Converts text with ANSI SGR escape codes to Hiccup markup with styled spans.
   Used for blessed output where we want actual visual styling."
  [text]
  (cond
    (nil? text) nil
    (not (string/includes? text "\u001b")) text
    :else
    (let [segments (parse-segments text)]
      (loop [remaining segments
             effects {}
             result [:<>]]
        (if-let [[seg-type seg-value] (first remaining)]
          (case seg-type
            :sgr (recur (rest remaining)
                        (apply-sgr effects seg-value)
                        result)
            :text (let [classes (effects->css-classes effects)]
                    (recur (rest remaining)
                           effects
                           (conj result
                                 (if classes
                                   [:span {:class classes} seg-value]
                                   seg-value)))))
          result)))))

(defn- opening-marker
  "Returns the opening pseudo-marker for an SGR code, or nil."
  [code]
  (cond
    (= code 1) "[B]"
    (= code 3) "[I]"
    (color-codes code) (str "[" (string/upper-case (color-names code)) "]")
    :else nil))

(defn- closing-markers
  "Returns closing pseudo-markers for all active effects (in reverse order).
   Effects map: {:bold bool, :italic bool, :color \"name\"}"
  [{:keys [bold italic color]}]
  ;; Close in reverse order: color, italic, bold
  (cond-> []
    color (conj (str "[/" (string/upper-case color) "]"))
    italic (conj "[/I]")
    bold (conj "[/B]")))

(defn ansi->markers
  "Converts text with ANSI SGR escape codes to plain text with visible pseudo-markers.
   Used for diff output where ANSI styling would be overridden by diff spans."
  [text]
  (cond
    (nil? text) nil
    (not (string/includes? text "\u001b")) text
    :else
    (let [segments (parse-segments text)
          sb (StringBuilder.)]
      (loop [remaining segments
             effects {}]
        (if-let [[seg-type seg-value] (first remaining)]
          (case seg-type
            :sgr (let [codes seg-value]
                   (if (and (= 1 (count codes)) (= 0 (first codes)))
                     ;; Reset: emit closing markers for all active effects
                     (do
                       (doseq [marker (closing-markers effects)]
                         (.append sb marker))
                       (recur (rest remaining) {}))
                     ;; Non-reset: emit opening markers
                     (let [new-effects (reduce (fn [eff code]
                                                 (when-let [m (opening-marker code)]
                                                   (.append sb m))
                                                 (cond
                                                   (= code 1) (assoc eff :bold true)
                                                   (= code 3) (assoc eff :italic true)
                                                   (color-codes code) (assoc eff :color (color-names code))
                                                   :else eff))
                                               effects
                                               codes)]
                       (recur (rest remaining) new-effects))))
            :text (do
                    (.append sb seg-value)
                    (recur (rest remaining) effects)))
          ;; End of segments: close any remaining effects
          (do
            (doseq [marker (closing-markers effects)]
              (.append sb marker))
            (.toString sb)))))))
