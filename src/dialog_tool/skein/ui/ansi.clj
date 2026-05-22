(ns dialog-tool.skein.ui.ansi
  "Converts ANSI SGR escape codes to either styled Hiccup markup (for blessed output)
   or visible pseudo-markers (for diff output where styling is overridden by diff spans).

   SGR effects are tracked as a stack. Each push adds an entry {:tag \"B\" :css \"ansi-bold\"},
   where :tag is the marker name (used in ansi->markers) and :css is the CSS class name
   (used in ansi->hiccup; nil means no CSS contribution). A SGR reset (code 0) unwinds
   the entire stack, emitting closing markers in reverse push order.

   SGR 38;2;R;G;B sets a 24-bit RGB foreground color. Known RGB values map to named colors;
   unknown values use the tag COLOR#RRGGBB with an inline CSS style.
   SGR 39 (default foreground) closes the most recently pushed color entry.
   
   TODO: There are known problems with how this code parses output from dgdebug, and especially,
   how it outputs CSS-styled markup when there are colors.
   
   Note that dgdebug renders italic text with the ANSI underlined code; we treat that
   as italic ('[I]') and render to screen in italic.
   
   Likewise, dgdebug renders reverse video to indicate underlined.
   
   In addition, this is organized around how dgdebug outputs ANSI escapes and does not account
   for differences in how frotz or other standard interpreters operate."
  (:require [clojure.string :as string]
            [dev.onionpancakes.chassis.core :as chassis]))

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

(def ^:private known-rgb-colors
  "Map from [r g b] triplet to color name, for dgdebug 24-bit color output."
  ;; See https://github.com/Dialog-IF/dialog/blob/6107864a00b56c1f9de2a63a70d4f99ec2241d39/src/term_tty.c#L173
  {[0x26 0x26 0x26] "black"
   [0xdf 0x20 0x50] "red"
   [0x18 0xaa 0x49] "green"
   [0xb5 0xa9 0x29] "yellow"
   [0x63 0x61 0xea] "blue"
   [0xbf 0x1c 0xbf] "magenta"
   [0x21 0xba 0xba] "cyan"
   [0xf2 0xf2 0xf2] "white"})

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
    (loop [pos      0
           segments []]
      (if (.find matcher)
        (let [match-start (.start matcher)
              match-end   (.end matcher)
              params      (parse-sgr (.group matcher 1))
              segments    (if (> match-start pos)
                            (conj segments [:text (subs text pos match-start)])
                            segments)]
          (recur match-end (conj segments [:sgr params])))
        ;; No more matches; emit trailing text
        (if (< pos (count text))
          (conj segments [:text (subs text pos)])
          segments)))))

(defn- rgb-entry
  "Creates a stack entry for a 24-bit RGB foreground color.
   Looks up known colors first; falls back to a COLOR#RRGGBB tag with inline style."
  [r g b]
  (if-let [name (known-rgb-colors [r g b])]
    {:tag (string/upper-case name) :css (str "ansi-" name) :color? true}
    (let [hex (format "%02X%02X%02X" r g b)]
      {:tag (str "#" hex) :css nil :style (str "color:#" (string/lower-case hex)) :color? true})))

(defn- code->entry
  "Converts a single SGR code to a stack entry, or nil for codes handled elsewhere
   (reset 0, default-foreground 39, or the multi-code RGB prefix 38)."
  [code]
  (cond
    (= code 0) nil
    (= code 1) {:tag "B" :css "ansi-bold"}
    (= code 4) {:tag "I" :css "ansi-italic"}
    (= code 7) {:tag "U" :css "ansi-underline"}
    (= code 50) {:tag "TT" :css "ansi-fixed"}
    (color-codes code) {:tag    (string/upper-case (color-names code))
                        :css    (str "ansi-" (color-names code))
                        :color? true}
    :else {:tag (str code) :css nil}))

(defn- sgr-actions
  "Converts a SGR params vector to a sequence of actions.
   Actions:
     [:reset]        — clear the entire stack
     [:pop-color]    — close and remove the most recent color entry
     [:push entry]   — push a new entry onto the stack
   Handles multi-code sequences: 38;2;R;G;B is consumed as one RGB push."
  [codes]
  (loop [[code & rest-codes :as remaining] codes
         actions []]
    (cond
      (empty? remaining)
      actions

      (= code 0)
      (recur rest-codes (conj actions [:reset]))

      (= code 39)
      (recur rest-codes (conj actions [:pop-color]))

      ;; 38;2;R;G;B — 24-bit RGB foreground color
      (and (= code 38)
           (= (first rest-codes) 2)
           (>= (count rest-codes) 4))
      (let [[_ r g b & tail] rest-codes]
        (recur tail (conj actions [:push (rgb-entry r g b)])))

      :else
      (recur rest-codes (conj actions [:push (code->entry code)])))))

(defn- pop-color-entry
  "Finds and removes the most recently pushed color entry (:color? true) from the stack.
   Returns [new-stack popped-entry], or [stack nil] if no color entry is present."
  [stack]
  (let [idx (reduce (fn [found i]
                      (if (:color? (nth stack i)) i found))
                    nil
                    (range (count stack)))]
    (if idx
      [(into (subvec stack 0 idx) (subvec stack (inc idx)))
       (nth stack idx)]
      [stack nil])))

(defn- apply-actions
  "Applies a sequence of sgr-actions to the stack. Returns the updated stack.
   (Used by ansi->hiccup; ansi->markers handles actions inline to interleave marker output.)"
  [stack actions]
  (reduce (fn [s [action entry]]
            (case action
              :reset []
              :pop-color (first (pop-color-entry s))
              :push (if entry (conj s entry) s)))
          stack
          actions))

(defn- stack->span-attrs
  "Derives a hiccup attrs map from the current stack.
   Combines CSS classes (from :css) and inline style (from :style entries).
   Returns nil when the stack contributes no visible styling."
  [stack]
  (let [classes (->> stack (keep :css) distinct (string/join " ") not-empty)
        style   (->> stack (keep :style) (string/join ";") not-empty)]
    (when (or classes style)
      (cond-> {}
        classes (assoc :class classes)
        style (assoc :style style)))))

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
             stack     []
             result    ^::chassis/content []]
        (if-let [[seg-type seg-value] (first remaining)]
          (case seg-type
            :sgr (recur (rest remaining)
                        (apply-actions stack (sgr-actions seg-value))
                        result)
            :text (let [attrs (stack->span-attrs stack)]
                    (recur (rest remaining)
                           stack
                           (conj result
                                 (if attrs
                                   [:span attrs seg-value]
                                   seg-value)))))
          result)))))

(defn- closing-tags
  "Returns closing pseudo-marker strings for all entries in the stack, in reverse push order."
  [stack]
  (map (fn [{:keys [tag]}] (str "[/" tag "]"))
       (reverse stack)))

(defn ansi->markers
  "Converts text with ANSI SGR escape codes to plain text with visible pseudo-markers.
   Used for diff output where ANSI styling would be overridden by diff spans.
   Unrecognized SGR codes use the numeric code as the tag name."
  [text]
  (cond
    (nil? text) nil
    (not (string/includes? text "\u001b")) text
    :else
    (let [segments (parse-segments text)
          sb       (StringBuilder.)]
      (loop [remaining segments
             stack     []]
        (if-let [[seg-type seg-value] (first remaining)]
          (case seg-type
            :sgr
            (let [new-stack
                  (reduce (fn [s [action entry]]
                            (case action
                              :reset
                              (do
                                (doseq [marker (closing-tags s)]
                                  (.append sb marker))
                                [])
                              :pop-color
                              (let [[s' popped] (pop-color-entry s)]
                                (when popped
                                  (.append sb (str "[/" (:tag popped) "]")))
                                s')
                              :push
                              (if entry
                                (do
                                  (.append sb (str "[" (:tag entry) "]"))
                                  (conj s entry))
                                s)))
                          stack
                          (sgr-actions seg-value))]
              (recur (rest remaining) new-stack))
            :text
            (do
              (.append sb seg-value)
              (recur (rest remaining) stack)))
          ;; End of segments: close any remaining effects
          (do
            (doseq [marker (closing-tags stack)]
              (.append sb marker))
            (.toString sb)))))))

(defn strip-ansi
  "Removes ANSI escape sequences from a string."
  [s]
  (string/replace s #"\x1b\[[0-9;]*m" ""))
