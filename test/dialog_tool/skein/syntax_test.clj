(ns dialog-tool.skein.syntax-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [dialog-tool.skein.syntax :as syntax]))

(defn- span [class text]
  (str "<span class=\"" class "\">" text "</span>"))

(deftest highlight-line-comments
  (testing "full-line comment"
    (is (= (span "dg-comment" "%% hello world")
           (syntax/highlight-line "%% hello world"))))

  (testing "indented comment"
    (is (= (str "  " (span "dg-comment" "%% hello"))
           (syntax/highlight-line "  %% hello"))))

  (testing "inline comment in body"
    (is (= (str "  some text " (span "dg-comment" "%% comment"))
           (syntax/highlight-line "  some text %% comment")))))

(deftest highlight-line-topic-declarations
  (testing "simple topic"
    (is (= (span "dg-heading" "#player")
           (syntax/highlight-line "#player"))))

  (testing "topic with comment"
    (is (= (str (span "dg-heading" "#room") " " (span "dg-comment" "%% defines room"))
           (syntax/highlight-line "#room %% defines room")))))

(deftest highlight-line-objects
  (testing "objects in body"
    (is (= (str "  " (span "dg-object" "#lamp") " is here")
           (syntax/highlight-line "  #lamp is here"))))

  (testing "objects in query"
    (is (= (str "  "
                (span "dg-predicate" "(")
                (span "dg-object" "#lamp")
                " is "
                (span "dg-object" "#heldby")
                " "
                (span "dg-object" "#player")
                (span "dg-predicate" ")"))
           (syntax/highlight-line "  (#lamp is #heldby #player)")))))

(deftest highlight-line-variables
  (testing "variable in body"
    (is (= (str "  " (span "dg-variable" "$Obj") " is here")
           (syntax/highlight-line "  $Obj is here"))))

  (testing "variable in query"
    (is (= (str "  "
                (span "dg-predicate" "(")
                "name "
                (span "dg-variable" "$X")
                (span "dg-predicate" ")"))
           (syntax/highlight-line "  (name $X)")))))

(deftest highlight-line-numbers
  (testing "standalone number in query"
    (is (= (str "  "
                (span "dg-predicate" "(")
                "foo "
                (span "dg-number" "42")
                (span "dg-predicate" ")"))
           (syntax/highlight-line "  (foo 42)")))))

(deftest highlight-line-keywords
  (testing "simple keywords"
    (is (= (str "  " (span "dg-keyword" "(if)") " "
                (span "dg-keyword" "(then)"))
           (syntax/highlight-line "  (if) (then)"))))

  (testing "at random"
    (is (= (str "  " (span "dg-keyword" "(at random)"))
           (syntax/highlight-line "  (at random)"))))

  (testing "select/or"
    (is (= (str "  " (span "dg-keyword" "(select)") " "
                (span "dg-keyword" "(or)") " a "
                (span "dg-keyword" "(or)") " b "
                (span "dg-keyword" "(at random)"))
           (syntax/highlight-line "  (select) (or) a (or) b (at random)"))))

  (testing "now keyword"
    (is (= (str "  " (span "dg-keyword" "(now)"))
           (syntax/highlight-line "  (now)"))))

  (testing "tilde as keyword"
    (is (= (str "  " (span "dg-keyword" "~")
                (span "dg-predicate" "(")
                "test"
                (span "dg-predicate" ")"))
           (syntax/highlight-line "  ~(test)")))))

(deftest highlight-line-dict-words
  (testing "dictionary word"
    (is (= (str "  " (span "dg-dict-word" "@hello") " world")
           (syntax/highlight-line "  @hello world")))))

(deftest highlight-line-escape-sequences
  (testing "escape in body"
    (is (= (str "  " (span "dg-escape" "\\n") " hello")
           (syntax/highlight-line "  \\n hello")))))

(deftest highlight-line-rule-heads
  (testing "simple rule head"
    (is (= (str (span "dg-predicate" "(") "name *" (span "dg-predicate" ")") " lamp")
           (syntax/highlight-line "(name *) lamp"))))

  (testing "access predicate rule"
    (is (= (str (span "dg-keyword" "@")
                (span "dg-predicate" "(") "name *" (span "dg-predicate" ")"))
           (syntax/highlight-line "@(name *)"))))

  (testing "negated rule"
    (is (= (str (span "dg-keyword" "~")
                (span "dg-predicate" "(") "name *" (span "dg-predicate" ")"))
           (syntax/highlight-line "~(name *)")))))

(deftest highlight-line-brackets
  (testing "list brackets"
    (is (= (str "  " (span "dg-punctuation" "[")
                (span "dg-object" "#a") " "
                (span "dg-object" "#b")
                (span "dg-punctuation" "]"))
           (syntax/highlight-line "  [#a #b]"))))

  (testing "closure braces"
    (is (= (str "  " (span "dg-punctuation" "{")
                (span "dg-predicate" "(") "foo" (span "dg-predicate" ")")
                (span "dg-punctuation" "}"))
           (syntax/highlight-line "  {(foo)}")))))

(deftest highlight-line-html-escaping
  (testing "angle brackets are escaped"
    (is (= "  &lt;b&gt;hello&lt;/b&gt;"
           (syntax/highlight-line "  <b>hello</b>"))))

  (testing "ampersands are escaped"
    (is (= "  foo &amp; bar"
           (syntax/highlight-line "  foo & bar")))))

(deftest highlight-line-blank-lines
  (testing "empty string"
    (is (= "" (syntax/highlight-line ""))))

  (testing "whitespace only"
    (is (= "   " (syntax/highlight-line "   ")))))
