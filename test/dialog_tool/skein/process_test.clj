(ns dialog-tool.skein.process-test
  (:require [clojure.test :refer [deftest is testing]]
            [dialog-tool.skein.process]))

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

(def ^:private close-ansi @#'dialog-tool.skein.process/close-ansi)

;; --- close-ansi ---

(deftest close-ansi-plain-text
  (testing "no SGR sequences, no change"
    (is (= "hello world" (close-ansi "hello world")))))

(deftest close-ansi-empty-string
  (is (= "" (close-ansi ""))))

(deftest close-ansi-already-reset
  (testing "last SGR is a reset, no change"
    (is (= (str BOLD "bold" RESET)
           (close-ansi (str BOLD "bold" RESET))))))

(deftest close-ansi-already-reset-with-trailing-newline
  (testing "last SGR is a reset before trailing newline, no change"
    (is (= (str BOLD "bold" RESET "\n")
           (close-ansi (str BOLD "bold" RESET "\n"))))))

(deftest close-ansi-unclosed-bold
  (testing "trailing bold SGR gets a reset appended"
    (is (= (str BOLD "bold" RESET)
           (close-ansi (str BOLD "bold"))))))

(deftest close-ansi-unclosed-underline
  (testing "trailing underline SGR gets a reset appended"
    (is (= (str UNDERLINE "underlined" RESET)
           (close-ansi (str UNDERLINE "underlined"))))))

(deftest close-ansi-unclosed-color
  (testing "trailing color SGR gets a reset appended"
    (is (= (str RED "error" RESET)
           (close-ansi (str RED "error"))))))

(deftest close-ansi-unclosed-with-trailing-newline
  (testing "reset inserted before trailing newline"
    (is (= (str BOLD "bold" RESET "\n")
           (close-ansi (str BOLD "bold\n"))))))

(deftest close-ansi-reset-then-new-sgr
  (testing "SGR after last reset triggers appended reset"
    (is (= (str BOLD "bold" RESET " " UNDERLINE "underlined" RESET)
           (close-ansi (str BOLD "bold" RESET " " UNDERLINE "underlined"))))))

(deftest close-ansi-reset-then-new-sgr-with-trailing-newline
  (testing "SGR after last reset, reset inserted before trailing newline"
    (is (= (str BOLD "bold" RESET " " UNDERLINE "underlined" RESET "\n")
           (close-ansi (str BOLD "bold" RESET " " UNDERLINE "underlined\n"))))))

(deftest close-ansi-multiple-resets-at-end
  (testing "redundant resets at end, no change"
    (is (= (str BOLD "bold" RESET RESET)
           (close-ansi (str BOLD "bold" RESET RESET))))))

(deftest close-ansi-only-reset
  (testing "string that is just a reset, no change"
    (is (= RESET (close-ansi RESET)))))

(deftest close-ansi-text-after-reset
  (testing "plain text after final reset, no extra reset needed"
    (is (= (str BOLD "bold" RESET " plain")
           (close-ansi (str BOLD "bold" RESET " plain"))))))

(deftest close-ansi-combined-sgr
  (testing "combined SGR params like 1;31 are recognized"
    (is (= (str (sgr 1 31) "alert" RESET)
           (close-ansi (str (sgr 1 31) "alert"))))))

(deftest close-ansi-bare-esc-bracket-m
  (testing "ESC[m (no params) is semantically a reset but not recognized as one;
            an extra reset is harmless, and Dialog doesn't emit this form"
    (let [bare-reset (str ESC "[m")]
      (is (= (str BOLD "bold" bare-reset RESET)
             (close-ansi (str BOLD "bold" bare-reset)))))))

(deftest close-ansi-dialog-death-response
  (testing "response ending with underline (like Dialog's death prompt) gets reset"
    (let [response (str "Thurg roars in fury.\n\n"
                        RESET BOLD "     *** You have died. ***     \n"
                        RESET "\n"
                        RESET UNDERLINE "Would you like to:\n"
                        "     UNDO the last move,\n"
                        "     or RESTART from the beginning?\n")]
      (is (= (str "Thurg roars in fury.\n\n"
                   RESET BOLD "     *** You have died. ***     \n"
                   RESET "\n"
                   RESET UNDERLINE "Would you like to:\n"
                   "     UNDO the last move,\n"
                   "     or RESTART from the beginning?" RESET "\n")
             (close-ansi response))))))

(deftest close-ansi-newline-only
  (testing "just a newline, no change"
    (is (= "\n" (close-ansi "\n")))))

(deftest close-ansi-multiple-trailing-newlines
  (testing "only the last newline counts as trailing"
    (is (= (str BOLD "bold\n" RESET "\n")
           (close-ansi (str BOLD "bold\n\n"))))))
