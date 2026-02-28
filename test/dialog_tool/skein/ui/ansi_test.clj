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
(def ITALIC (sgr 3))
(def UNDERLINE (sgr 4))
(def RED (sgr 31))
(def BLUE (sgr 34))

;; --- ansi->hiccup ---

(deftest hiccup-nil-input
  (is (nil? (ansi->hiccup nil))))

(deftest hiccup-plain-text
  (is (= "hello world" (ansi->hiccup "hello world"))))

(deftest hiccup-bold
  (is (= [:<> [:span {:class "ansi-bold"} "hello"]]
         (ansi->hiccup (str BOLD "hello")))))

(deftest hiccup-italic
  (is (= [:<> [:span {:class "ansi-italic"} "hello"]]
         (ansi->hiccup (str ITALIC "hello")))))

(deftest hiccup-underline
  (is (= [:<> [:span {:class "ansi-underline"} "hello"]]
         (ansi->hiccup (str UNDERLINE "hello")))))

(deftest hiccup-color
  (is (= [:<> [:span {:class "ansi-red"} "error"]]
         (ansi->hiccup (str RED "error")))))

(deftest hiccup-bold-and-color
  (is (= [:<> [:span {:class "ansi-bold ansi-blue"} "title"]]
         (ansi->hiccup (str BOLD BLUE "title")))))

(deftest hiccup-reset-clears-effects
  (is (= [:<> [:span {:class "ansi-bold"} "bold"] " normal"]
         (ansi->hiccup (str BOLD "bold" RESET " normal")))))

(deftest hiccup-mixed-styled-and-unstyled
  (is (= [:<> "before " [:span {:class "ansi-red"} "red"] " after"]
         (ansi->hiccup (str "before " RED "red" RESET " after")))))

(deftest hiccup-color-change
  (testing "changing color replaces previous color"
    (is (= [:<> [:span {:class "ansi-red"} "red"] [:span {:class "ansi-blue"} "blue"]]
           (ansi->hiccup (str RED "red" BLUE "blue"))))))

(deftest hiccup-combined-sgr-params
  (testing "CSI 1;34 m sets bold+blue in one sequence"
    (is (= [:<> [:span {:class "ansi-bold ansi-blue"} "both"]]
           (ansi->hiccup (str ESC "[1;34m" "both"))))))

(deftest hiccup-bare-esc-bracket-m-is-reset
  (testing "ESC[m with no params is treated as reset"
    (is (= [:<> [:span {:class "ansi-bold"} "bold"] " normal"]
           (ansi->hiccup (str BOLD "bold" ESC "[m" " normal"))))))

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
         (ansi->markers (str ITALIC "hello" RESET)))))

(deftest markers-underline
  (is (= "[U]hello[/U]"
         (ansi->markers (str UNDERLINE "hello" RESET)))))

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

(deftest markers-unrecognized-sgr-code
  (testing "unrecognized SGR codes are represented as [?] / [/?]"
    ;; SGR 6 is "rapid blink", not something we support
    (is (= "[?]hello[/?]"
           (ansi->markers (str (sgr 6) "hello" RESET))))))

;; --- strip-ansi ---

(deftest strip-ansi-removes-sgr
  (is (= "hello world"
         (strip-ansi (str BOLD "hello" RESET " " UNDERLINE "world" RESET)))))
