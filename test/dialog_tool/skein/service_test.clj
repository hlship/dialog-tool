(ns dialog-tool.skein.service-test
  "Integration tests for the Skein web UI.

  Runs a real http-kit server backed by a real dgdebug process
  against the test-fixtures/dgsample Dialog project."
  (:require [clojure.string :as string]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [cheshire.core :as json]
            [dialog-tool.skein.service :as service]
            [org.httpkit.client :as http]))

(def ^:private test-port 19876)

(def ^:private base-url (str "http://localhost:" test-port))

(def ^:private project-dir "test-fixtures/dgsample")

(def ^:private test-seed 42)

(use-fixtures :once
  (fn [tests]
    (service/start! project-dir
                    {:skein-path "/tmp/test.skein"
                     :port test-port
                     :seed test-seed
                     :engine :dgdebug
                     ;; Prevent System/exit on shutdown
                     :development-mode? true})
    (try
      (tests)
      (finally
        (when-let [shutdown @service/*shutdown]
          (shutdown))))))

(defn- GET
  "Issues a GET request, returns the deref'd response."
  [path]
  @(http/get (str base-url path)))

(defn- POST
  "Issues a POST request with optional JSON body, returns the deref'd response."
  ([path]
   (POST path nil))
  ([path body]
   @(http/post (str base-url path)
               (when body
                 {:headers {"Content-Type" "application/json"}
                  :body (json/generate-string body)}))))

(defn- content-type
  "Extracts content-type from http-kit response headers."
  [response]
  (get-in response [:headers :content-type]))

;; ---------------------------------------------------------------------------
;; Tests
;; ---------------------------------------------------------------------------

(deftest index-page-test
  (let [{:keys [status body]} (GET "/")]
    (is (= 200 status))
    (is (string? body))
    (is (string/includes? body "<title>Dialog Skein</title>"))
    (is (string/includes? body "data-init"))))

(deftest static-resource-test
  (testing "datastar.js is served"
    (let [{:keys [status]} (GET "/js/datastar.js")]
      (is (= 200 status))))
  (testing "main.js is served"
    (let [{:keys [status]} (GET "/js/main.js")]
      (is (= 200 status)))))

(deftest app-sse-test
  (let [response (GET "/app")
        {:keys [status body]} response
        ct (content-type response)]
    (is (= 200 status))
    (is (some? ct))
    (is (string/includes? ct "text/event-stream"))
    (is (string? body))
    ;; SSE body should contain Datastar fragment events
    (is (string/includes? body "datastar"))
    ;; Should contain the game's initial output
    (is (string/includes? body "Featureless Space"))))

(deftest select-knot-test
  (let [response (GET "/action/select/0")
        {:keys [status]} response
        ct (content-type response)]
    (is (= 200 status))
    (is (some? ct))
    (is (string/includes? ct "text/event-stream"))))

(deftest new-command-test
  (let [{:keys [status]} (POST "/action/new-command/0"
                           {:newCommand "look"})]
    (is (= 200 status))
    (let [knots (-> @service/*session :tree :knots)]
      (is (> (count knots) 1))
      (let [new-knot (->> (vals knots)
                          (remove #(zero? (:id %)))
                          first)]
        (is (some? new-knot))
        (is (= "look" (:command new-knot)))
        ;; Should have the game's response to "look"
        (let [response (or (:unblessed new-knot) (:response new-knot))]
          (is (some? response))
          (is (string/includes? response "Featureless Space")))))))

(deftest bless-knot-test
  ;; Add a command first
  (POST "/action/new-command/0" {:newCommand "x orb"})
  (let [new-knot-id (->> @service/*session :tree :knots vals
                         (remove #(zero? (:id %)))
                         (some #(when (= "x orb" (:command %)) (:id %))))
        {:keys [status]} (POST (str "/action/bless/" new-knot-id))]
    (is (= 200 status))
    ;; After blessing, unblessed should be nil and response should be set
    (let [knot (get-in @service/*session [:tree :knots new-knot-id])]
      (is (nil? (:unblessed knot)))
      (is (some? (:response knot))))))

(deftest undo-test
  ;; Add a command so we have something to undo
  (POST "/action/new-command/0" {:newCommand "i"})
  (let [knot-count-before (count (-> @service/*session :tree :knots))
        {:keys [status]} (GET "/action/undo")]
    (is (= 200 status))
    (let [knot-count-after (count (-> @service/*session :tree :knots))]
      (is (< knot-count-after knot-count-before)))))
