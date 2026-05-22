(ns dialog-tool.skein.ui.ansi-test
  (:require [clojure.test :refer [deftest is testing]]
            [dialog-tool.skein.ui.ansi :refer [ansi->hiccup ansi->markers strip-ansi]]))

(def ESC "\u001b")

(defn sgr
  "Build an SGR escape sequence from numeric codes."
  [& codes]
  (str ESC "[" (apply str (interpose ";" codes)) "m"))

(def RESET (sgr 0))
(def BOLD (sgr 1))
;; TODO: Should be engine aware (unless dfrotz does the same thing)
(def UNDERLINE (sgr 4))                                     ; ANSI underline is dgdebug's way of showing italic
(def REVERSE (sgr 7))                                       ; ANSI reverse is dgdebug's way of showing underline
(def TT (sgr 50))
(def RED (sgr 31))
(def BLUE (sgr 34))
(def DEFAULT-FG (sgr 39))
(def RGB-UNKNOWN (sgr 38 2 0xaa 0xbb 0xcc))                         ; unknown color, uses COLOR# tag

;; --- ansi->hiccup ---

(deftest hiccup-nil-input
  (is (nil? (ansi->hiccup nil))))

(deftest hiccup-plain-text
  (is (= "hello world" (ansi->hiccup "hello world"))))

(deftest hiccup-bold
  (is (= [[:span {:class "ansi-bold"} "hello"]]
         (ansi->hiccup (str BOLD "hello")))))

(deftest hiccup-italic
  (is (= [[:span {:class "ansi-italic"} "hello"]]
         (ansi->hiccup (str UNDERLINE "hello")))))

(deftest hiccup-underline
  (is (= [[:span {:class "ansi-underline"} "hello"]]
         (ansi->hiccup (str REVERSE "hello")))))

(deftest hiccup-color
  (is (= [[:span {:class "ansi-red"} "error"]]
         (ansi->hiccup (str RED "error")))))

(deftest hiccup-bold-and-color
  (is (= [[:span {:class "ansi-bold ansi-blue"} "title"]]
         (ansi->hiccup (str BOLD BLUE "title")))))

(deftest hiccup-reset-clears-effects
  (is (= [[:span {:class "ansi-bold"} "bold"] " normal"]
         (ansi->hiccup (str BOLD "bold" RESET " normal")))))

(deftest hiccup-mixed-styled-and-unstyled
  (is (= ["before " [:span {:class "ansi-red"} "red"] " after"]
         (ansi->hiccup (str "before " RED "red" RESET " after")))))

(deftest hiccup-color-change
  (testing "changing color after reset starts fresh"
    (is (= [[:span {:class "ansi-red"} "red"] [:span {:class "ansi-blue"} "blue"]]
           (ansi->hiccup (str RED "red" RESET BLUE "blue"))))))

(deftest hiccup-combined-sgr-params
  (testing "CSI 1;34 m sets bold+blue in one sequence"
    (is (= [[:span {:class "ansi-bold ansi-blue"} "both"]]
           (ansi->hiccup (str ESC "[1;34m" "both"))))))

(deftest hiccup-fixed
  (is (= [[:span {:class "ansi-fixed"} "output"]]
         (ansi->hiccup (str TT "output")))))

(deftest hiccup-fixed-and-bold
  (is (= [[:span {:class "ansi-bold ansi-fixed"} "output"]]
         (ansi->hiccup (str BOLD TT "output")))))

(deftest hiccup-bare-esc-bracket-m-is-reset
  (testing "ESC[m with no params is treated as reset"
    (is (= [[:span {:class "ansi-bold"} "bold"] " normal"]
           (ansi->hiccup (str BOLD "bold" ESC "[m" " normal"))))))

(deftest hiccup-rgb-unknown-color
  (testing "38;2;R;G;B with unknown color uses inline style"
    (is (= [[:span {:style "color:#aabbcc"} "pink"]]
           (ansi->hiccup (str RGB-UNKNOWN "pink"))))))

(deftest hiccup-rgb-default-fg-closes-color
  (testing "SGR 39 removes the color from the stack"
    (is (= [[:span {:style "color:#aabbcc"} "pink"] " normal"]
           (ansi->hiccup (str RGB-UNKNOWN "pink" DEFAULT-FG " normal"))))))

;; --- ansi->markers ---

(deftest markers-nil-input
  (is (nil? (ansi->markers nil))))

(deftest markers-plain-text
  (is (= "hello world" (ansi->markers "hello world"))))

(deftest markers-bold
  (is (= "[B]hello[/B]"
         (ansi->markers (str BOLD "hello" RESET)))))

(deftest markers-italic
  (is (= "[I]hello[/I]"
         (ansi->markers (str UNDERLINE "hello" RESET)))))

(deftest markers-underline
  (is (= "[U]hello[/U]"
         (ansi->markers (str REVERSE "hello" RESET)))))

(deftest markers-color
  (is (= "[RED]error[/RED]"
         (ansi->markers (str RED "error" RESET)))))

(deftest markers-bold-and-color
  (testing "reset closes all active effects"
    (is (= "[B][BLUE]title[/BLUE][/B]"
           (ansi->markers (str BOLD BLUE "title" RESET))))))

(deftest markers-mixed-text
  (is (= "before [RED]red[/RED] after"
         (ansi->markers (str "before " RED "red" RESET " after")))))

(deftest markers-unclosed-effects-closed-at-end
  (testing "effects still active at end of text get closing markers"
    (is (= "[B]bold[/B]"
           (ansi->markers (str BOLD "bold"))))))

(deftest markers-multiple-resets
  (testing "reset with no active effects is harmless"
    (is (= "[B]bold[/B] normal"
           (ansi->markers (str BOLD "bold" RESET RESET " normal"))))))

(deftest markers-combined-sgr-params
  (testing "CSI 1;31 m emits both markers"
    (is (= "[B][RED]alert[/RED][/B]"
           (ansi->markers (str ESC "[1;31m" "alert" RESET))))))

(deftest markers-fixed
  (is (= "[TT]output[/TT]"
         (ansi->markers (str TT "output" RESET)))))

(deftest markers-fixed-unclosed
  (is (= "[TT]output[/TT]"
         (ansi->markers (str TT "output")))))

(deftest markers-unrecognized-sgr-code
  (testing "unrecognized SGR codes use the numeric code as the tag"
    ;; SGR 6 is "rapid blink", not something we support
    (is (= "[6]hello[/6]"
           (ansi->markers (str (sgr 6) "hello" RESET))))))

(deftest markers-rgb-unknown-color
  (testing "38;2;R;G;B with unknown color uses COLOR#RRGGBB tag"
    (is (= "[#AABBCC]pink[/#AABBCC]"
           (ansi->markers (str RGB-UNKNOWN "pink" RESET))))))

(deftest markers-default-fg-closes-color
  (testing "SGR 39 closes the most recent color entry"
    (is (= "[RED]bold-red[/RED] plain"
           (ansi->markers (str RED "bold-red" DEFAULT-FG " plain"))))))

(deftest markers-default-fg-with-bold-underneath
  (testing "SGR 39 closes only the color, leaving bold active"
    (is (= "[B][RED]bold-red[/RED]still-bold[/B]"
           (ansi->markers (str BOLD RED "bold-red" DEFAULT-FG "still-bold" RESET))))))

;; --- strip-ansi ---

(deftest strip-ansi-removes-sgr
  (is (= "hello world"
         (strip-ansi (str BOLD "hello" RESET " " REVERSE "world" RESET)))))
