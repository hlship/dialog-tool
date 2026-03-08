(ns dialog-tool.skein.syntax
  "Syntax highlighting for Dialog source files (.dg).

  Tokenizes a line of Dialog source code and returns an HTML string with
  `<span class=\"dg-*\">` elements for each recognized token. The token types
  and their recognition rules are based on the TextMate grammar from
  https://github.com/sideburns3000/vscode-dialog-language

  The highlighter works line-by-line, which is sufficient for Dialog since
  the language has no multi-line string literals or block comments."
  (:require
    [clojure.string :as string]))

;; ---------------------------------------------------------------------------
;; HTML escaping (must happen BEFORE wrapping in spans)
;; ---------------------------------------------------------------------------

(defn html-escape
  [s]
  (-> s
      (string/replace "&" "&amp;")
      (string/replace "<" "&lt;")
      (string/replace ">" "&gt;")
      (string/replace "\"" "&quot;")))

;; ---------------------------------------------------------------------------
;; Token types and their CSS classes
;; ---------------------------------------------------------------------------

(def ^:private token-classes
  {:comment     "dg-comment"
   :keyword     "dg-keyword"
   :object      "dg-object"
   :variable    "dg-variable"
   :number      "dg-number"
   :dict-word   "dg-dict-word"
   :predicate   "dg-predicate"
   :heading     "dg-heading"
   :escape      "dg-escape"
   :punctuation "dg-punctuation"
   :error       "dg-error"})

(defn- wrap
  "Wraps text in a span with the given token class. Text must already be HTML-escaped."
  [token-type escaped-text]
  (if-let [css-class (token-classes token-type)]
    (str "<span class=\"" css-class "\">" escaped-text "</span>")
    escaped-text))

;; ---------------------------------------------------------------------------
;; Keyword recognition
;; ---------------------------------------------------------------------------

(def ^:private keyword-set
  "Dialog keywords that appear as (keyword) with no additional arguments."
  #{"if" "then" "elseif" "else" "endif"
    "select" "or" "stopping" "cycling" "exhaust"
    "now" "just" "stoppable" "log" "link"
    "collect words" "from words"})

(def ^:private keyword-with-args-re
  "Pattern for keywords that take arguments: (collect ...), (accumulate ...), etc.
   These need the keyword portion highlighted differently from the arguments."
  #"(?i)collect|accumulate|into|determine\s+object|matching\s+all\s+of|span|div|(?:inline\s+)?status\s+bar|link(?:\s+resource)?")

(def ^:private at-random-re
  "Pattern for the various forms of 'at random': (at random), (purely at random), (then at random), etc."
  #"(?:then\s+)?(?:purely\s+)?at\s+random")

(defn- keyword-content?
  "Returns true if the trimmed content inside parentheses is a Dialog keyword."
  [s]
  (let [trimmed (string/trim s)]
    (or (contains? keyword-set (string/lower-case trimmed))
        (re-matches at-random-re (string/lower-case trimmed)))))

;; ---------------------------------------------------------------------------
;; Tokenizer — works on a single line, producing HTML
;; ---------------------------------------------------------------------------

(defn- tokenize-inside-parens
  "Tokenizes content inside parentheses (after the opening paren, before the closing).
   Returns HTML string. `keyword?` indicates if the whole expression is a keyword."
  [content kw?]
  (let [sb (StringBuilder.)
        len (count content)
        ;; Tokenize content character by character
        i (volatile! 0)]
    (while (< @i len)
      (let [ch (.charAt content (int @i))]
        (cond
          ;; Nested parentheses — find matching close
          (= ch \()
          (let [start @i
                depth (volatile! 1)
                j (volatile! (inc @i))]
            (while (and (< @j len) (pos? @depth))
              (let [c (.charAt content (int @j))]
                (cond
                  (= c \() (vswap! depth inc)
                  (= c \)) (vswap! depth dec))
                (vswap! j inc)))
            ;; Extract the full nested expression including parens
            (let [nested (subs content start @j)
                  inner (subs content (inc start) (dec @j))
                  nested-kw? (keyword-content? inner)]
              (if nested-kw?
                (.append sb (wrap :keyword (html-escape nested)))
                (do
                  (.append sb (wrap :predicate (html-escape "(")))
                  (.append sb (tokenize-inside-parens inner false))
                  (.append sb (wrap :predicate (html-escape ")"))))))
            (vreset! i @j))

          ;; Comment
          (and (= ch \%) (< (inc @i) len) (= (.charAt content (int (inc @i))) \%))
          (let [rest-of-line (subs content @i)]
            (.append sb (wrap :comment (html-escape rest-of-line)))
            (vreset! i len))

          ;; Variable $...
          (= ch \$)
          (let [start @i
                j (volatile! (inc @i))]
            (while (and (< @j len)
                        (let [c (.charAt content (int @j))]
                          (or (Character/isLetterOrDigit c)
                              (#{\_ \+ \- \< \>} c))))
              (vswap! j inc))
            (.append sb (wrap :variable (html-escape (subs content start @j))))
            (vreset! i @j))

          ;; Object #...
          (= ch \#)
          (let [start @i
                j (volatile! (inc @i))]
            (while (and (< @j len)
                        (let [c (.charAt content (int @j))]
                          (or (Character/isLetterOrDigit c)
                              (#{\_ \+ \-} c))))
              (vswap! j inc))
            (.append sb (wrap :object (html-escape (subs content start @j))))
            (vreset! i @j))

          ;; Dictionary word @...
          (= ch \@)
          (let [start @i
                j (volatile! (inc @i))]
            (while (and (< @j len)
                        (let [c (.charAt content (int @j))]
                          (not (or (Character/isWhitespace c)
                                   (#{\# \$ \@ \~ \* \| \( \) \[ \] \{ \} \%} c)))))
              (vswap! j inc))
            (.append sb (wrap :dict-word (html-escape (subs content start @j))))
            (vreset! i @j))

          ;; Number (standalone, not part of identifier)
          (and (Character/isDigit ch)
               (or (zero? @i)
                   (let [prev (.charAt content (int (dec @i)))]
                     (or (Character/isWhitespace prev)
                         (#{\( \[ \{ \|} prev)))))
          (let [start @i
                j (volatile! (inc @i))]
            (while (and (< @j len) (Character/isDigit (.charAt content (int @j))))
              (vswap! j inc))
            ;; Check it's followed by a word boundary
            (if (or (= @j len)
                    (let [next-ch (.charAt content (int @j))]
                      (or (Character/isWhitespace next-ch)
                          (#{\) \] \} \| \%} next-ch))))
              (do
                (.append sb (wrap :number (html-escape (subs content start @j))))
                (vreset! i @j))
              ;; Not a standalone number, just emit as text
              (do
                (.append sb (html-escape (str ch)))
                (vswap! i inc))))

          ;; Escape sequence \x
          (= ch \\)
          (if (< (inc @i) len)
            (let [escaped-pair (subs content @i (+ @i 2))]
              (.append sb (wrap :escape (html-escape escaped-pair)))
              (vswap! i + 2))
            (do
              (.append sb (html-escape (str ch)))
              (vswap! i inc)))

          ;; List brackets
          (or (= ch \[) (= ch \]))
          (do
            (.append sb (wrap :punctuation (html-escape (str ch))))
            (vswap! i inc))

          ;; Default: plain text
          :else
          (do
            (.append sb (html-escape (str ch)))
            (vswap! i inc)))))
    (str sb)))

(defn- tokenize-body
  "Tokenizes an indented line (rule body). Returns HTML string."
  [line]
  (let [sb (StringBuilder.)
        len (count line)
        i (volatile! 0)]
    (while (< @i len)
      (let [ch (.charAt line (int @i))]
        (cond
          ;; Comment
          (and (= ch \%) (< (inc @i) len) (= (.charAt line (int (inc @i))) \%))
          (let [rest-of-line (subs line @i)]
            (.append sb (wrap :comment (html-escape rest-of-line)))
            (vreset! i len))

          ;; Parenthesized expression — find matching close paren
          (= ch \()
          (let [start @i
                depth (volatile! 1)
                j (volatile! (inc @i))]
            (while (and (< @j len) (pos? @depth))
              (let [c (.charAt line (int @j))]
                (cond
                  (= c \() (vswap! depth inc)
                  (= c \)) (vswap! depth dec))
                (vswap! j inc)))
            (let [full-expr (subs line start @j)
                  inner (subs line (inc start) (dec @j))
                  kw? (keyword-content? inner)]
              (if kw?
                (.append sb (wrap :keyword (html-escape full-expr)))
                (do
                  (.append sb (wrap :predicate (html-escape "(")))
                  (.append sb (tokenize-inside-parens inner false))
                  (.append sb (wrap :predicate (html-escape ")"))))))
            (vreset! i @j))

          ;; Variable $...
          (= ch \$)
          (let [start @i
                j (volatile! (inc @i))]
            (while (and (< @j len)
                        (let [c (.charAt line (int @j))]
                          (or (Character/isLetterOrDigit c)
                              (#{\_ \+ \- \< \>} c))))
              (vswap! j inc))
            (.append sb (wrap :variable (html-escape (subs line start @j))))
            (vreset! i @j))

          ;; Object #...
          (= ch \#)
          (let [start @i
                j (volatile! (inc @i))]
            (while (and (< @j len)
                        (let [c (.charAt line (int @j))]
                          (or (Character/isLetterOrDigit c)
                              (#{\_ \+ \-} c))))
              (vswap! j inc))
            (.append sb (wrap :object (html-escape (subs line start @j))))
            (vreset! i @j))

          ;; Dictionary word @...
          (= ch \@)
          (let [start @i
                j (volatile! (inc @i))]
            (while (and (< @j len)
                        (let [c (.charAt line (int @j))]
                          (not (or (Character/isWhitespace c)
                                   (#{\# \$ \@ \~ \* \| \( \) \[ \] \{ \} \%} c)))))
              (vswap! j inc))
            (.append sb (wrap :dict-word (html-escape (subs line start @j))))
            (vreset! i @j))

          ;; Number (standalone)
          (and (Character/isDigit ch)
               (or (zero? @i)
                   (let [prev (.charAt line (int (dec @i)))]
                     (or (Character/isWhitespace prev)
                         (#{\( \[ \{ \|} prev)))))
          (let [start @i
                j (volatile! (inc @i))]
            (while (and (< @j len) (Character/isDigit (.charAt line (int @j))))
              (vswap! j inc))
            (if (or (= @j len)
                    (let [next-ch (.charAt line (int @j))]
                      (or (Character/isWhitespace next-ch)
                          (#{\) \] \} \| \%} next-ch))))
              (do
                (.append sb (wrap :number (html-escape (subs line start @j))))
                (vreset! i @j))
              (do
                (.append sb (html-escape (str ch)))
                (vswap! i inc))))

          ;; Escape sequence \x
          (= ch \\)
          (if (< (inc @i) len)
            (let [escaped-pair (subs line @i (+ @i 2))]
              (.append sb (wrap :escape (html-escape escaped-pair)))
              (vswap! i + 2))
            (do
              (.append sb (html-escape (str ch)))
              (vswap! i inc)))

          ;; Curly braces (closures)
          (or (= ch \{) (= ch \}))
          (do
            (.append sb (wrap :punctuation (html-escape (str ch))))
            (vswap! i inc))

          ;; Square brackets (lists)
          (or (= ch \[) (= ch \]))
          (do
            (.append sb (wrap :punctuation (html-escape (str ch))))
            (vswap! i inc))

          ;; Tilde (negation)
          (= ch \~)
          (do
            (.append sb (wrap :keyword (html-escape "~")))
            (vswap! i inc))

          ;; Default: plain text
          :else
          (do
            (.append sb (html-escape (str ch)))
            (vswap! i inc)))))
    (str sb)))

(defn- tokenize-rule-head
  "Tokenizes a rule definition line starting at the beginning.
   Handles optional @ prefix (access predicate) and ~ (negation),
   then the parenthesized rule head, then any body continuation."
  [line]
  (let [sb (StringBuilder.)
        len (count line)
        i (volatile! 0)]
    ;; Optional @ prefix
    (when (and (< @i len) (= (.charAt line (int @i)) \@))
      (.append sb (wrap :keyword (html-escape "@")))
      (vswap! i inc))
    ;; Optional ~ prefix
    (when (and (< @i len) (= (.charAt line (int @i)) \~))
      (.append sb (wrap :keyword (html-escape "~")))
      (vswap! i inc))
    ;; Opening paren of rule head
    (when (and (< @i len) (= (.charAt line (int @i)) \())
      (let [start @i
            depth (volatile! 1)
            j (volatile! (inc @i))]
        (while (and (< @j len) (pos? @depth))
          (let [c (.charAt line (int @j))]
            (cond
              (= c \() (vswap! depth inc)
              (= c \)) (vswap! depth dec))
            (vswap! j inc)))
        ;; Rule head: opening paren, inner content, closing paren
        (let [inner (subs line (inc start) (dec @j))]
          (.append sb (wrap :predicate (html-escape "(")))
          (.append sb (tokenize-inside-parens inner false))
          (.append sb (wrap :predicate (html-escape ")"))))
        (vreset! i @j)))
    ;; Rest of line is body
    (when (< @i len)
      (.append sb (tokenize-body (subs line @i))))
    (str sb)))

(defn- tokenize-topic-declaration
  "Tokenizes a current topic declaration line (starts with #identifier)."
  [line]
  (let [sb (StringBuilder.)
        len (count line)
        ;; Match #identifier at start
        j (volatile! 1)]
    (while (and (< @j len)
                (let [c (.charAt line (int @j))]
                  (or (Character/isLetterOrDigit c)
                      (#{\_ \+ \-} c))))
      (vswap! j inc))
    (.append sb (wrap :heading (html-escape (subs line 0 @j))))
    ;; Rest of line might be a comment
    (when (< @j len)
      (let [rest-str (subs line @j)]
        (if-let [comment-idx (string/index-of rest-str "%%")]
          (do
            (.append sb (html-escape (subs rest-str 0 comment-idx)))
            (.append sb (wrap :comment (html-escape (subs rest-str comment-idx)))))
          (.append sb (html-escape rest-str)))))
    (str sb)))

(defn- tokenize-special-declaration
  "Tokenizes special declarations like (global variable ...) and (generate N ...)."
  [line]
  ;; For now, treat these like rule heads — the parenthesized part
  ;; gets keyword + predicate highlighting
  (tokenize-rule-head line))

(defn highlight-line
  "Tokenizes a single line of Dialog source and returns an HTML string
   with <span class=\"dg-*\"> elements for syntax highlighting.

   The returned HTML is safe to insert directly (text content is HTML-escaped)."
  [line]
  (cond
    ;; Empty line
    (string/blank? line)
    (html-escape line)

    ;; Full-line comment
    (string/starts-with? (string/triml line) "%%")
    (let [leading-ws (re-find #"^\s*" line)
          rest (subs line (count leading-ws))]
      (str (html-escape leading-ws) (wrap :comment (html-escape rest))))

    ;; Current topic declaration: line starts with #
    (= (.charAt line 0) \#)
    (tokenize-topic-declaration line)

    ;; Rule definition: line starts with optional @~ then (
    (re-find #"^(@|~)?\(" line)
    (tokenize-rule-head line)

    ;; Special declarations: (global variable ...) or (generate ...)
    (re-find #"^\(\s*(?:global\s+variable|interface|generate\s+)" line)
    (tokenize-special-declaration line)

    ;; Indented line (rule body)
    (Character/isWhitespace (.charAt line 0))
    (tokenize-body line)

    ;; Anything else — just escape
    :else
    (html-escape line)))
