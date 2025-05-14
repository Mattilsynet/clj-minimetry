(ns open-telemetry.core-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [open-telemetry.core :as otel]))

(defn blur-stacklet [stacklet]
  (->> (for [line (str/split stacklet #"\n")]
         (str/replace line #"\$.+" "\\$..."))
       (take 2)))

(deftest flatten-data-test
  (testing "Flattens a map of data"
    (is (= (otel/flatten-data
            {"accept" "*/*"
             "host" "localhost:5555"
             "user-agent" "curl/8.6.0"}
            {:prefix "http.request.header"})
           {"http.request.header.accept" "*/*"
            "http.request.header.host" "localhost:5555"
            "http.request.header.user-agent" "curl/8.6.0"})))

  (testing "Flattens data without explicit prefix"
    (is (= (otel/flatten-data
            {"accept" "*/*"
             "host" "localhost:5555"
             "user-agent" "curl/8.6.0"})
           {"accept" "*/*"
            "host" "localhost:5555"
            "user-agent" "curl/8.6.0"})))

  (testing "Flattens collections"
    (is (= (otel/flatten-data
            [{"accept" "*/*"}
             {"host" "localhost:5555"}
             {"host" "localhost:8209"}
             {"user-agent" "curl/8.6.0"}])
           {"0.accept" "*/*"
            "1.host" "localhost:5555"
            "2.host" "localhost:8209"
            "3.user-agent" "curl/8.6.0"})))

  (testing "Flattens exception data with nested cause"
    (is (= (-> (otel/exception->data
                (ex-info "Oh no!" {:data "Here"}
                         (ex-info "Cause" {:lol "sob"})))
               (update :stacklet blur-stacklet)
               (update-in [:cause :stacklet] blur-stacklet)
               (otel/flatten-data {:prefix "exception"}))
           {"exception.message" "Oh no!"
            "exception.ex-data.data" "Here"
            "exception.stacklet" ["open_telemetry.core_test$..."
                                  "open_telemetry.core_test$..."]
            "exception.cause.message" "Cause"
            "exception.cause.ex-data.lol" "sob"
            "exception.cause.stacklet" ["open_telemetry.core_test$..."
                                        "open_telemetry.core_test$..."]})))

  (testing "Truncates some keys when flattening data"
    (is (= (-> (otel/exception->data
                (ex-info "Oh no!" {:data "Here"}
                         (ex-info "Cause" {:lol "sob"})))
               (update :stacklet blur-stacklet)
               (update-in [:cause :stacklet] blur-stacklet)
               (otel/flatten-data {:prefix "exception"
                                   :truncate-ks [:message]}))
           {"exception.message" "Oh ..."
            "exception.ex-data.data" "Here"
            "exception.stacklet" ["open_telemetry.core_test$..."
                                  "open_telemetry.core_test$..."]
            "exception.cause.message" "Cau..."
            "exception.cause.ex-data.lol" "sob"
            "exception.cause.stacklet" ["open_telemetry.core_test$..."
                                        "open_telemetry.core_test$..."]})))

  ;; GCP "optimaliserer" bort faktiske nils, so hold på hatten 💩🙄
  (testing "Produces explicit top-level nils"
    (is (= (otel/flatten-data nil {:prefix "_.req"})
           {"_.req" "nil"})))

  (testing "Produces explicit nil keys"
    (is (= (otel/flatten-data {:k nil} {:prefix "_.req"})
           {"_.req.k" "nil"}))))

(deftest exception->data-test
  (testing "Represents exception as data"
    (is (= (-> (otel/exception->data (ex-info "Oh no!" {:data "Here"}))
               (update :stacklet blur-stacklet))
           {:message "Oh no!"
            :ex-data {:data "Here"}
            :stacklet ["open_telemetry.core_test$..."
                       "open_telemetry.core_test$..."]})))

  (testing "Represents exception with cause as data"
    (is (= (-> (otel/exception->data (ex-info "Oh no!" {:data "Here"}
                                             (ex-info "Reason" {:lol "sob"})))
               (update :stacklet blur-stacklet)
               (update-in [:cause :stacklet] blur-stacklet))
           {:message "Oh no!"
            :ex-data {:data "Here"}
            :stacklet ["open_telemetry.core_test$..."
                       "open_telemetry.core_test$..."]
            :cause {:message "Reason"
                    :ex-data {:lol "sob"}
                    :stacklet ["open_telemetry.core_test$..."
                               "open_telemetry.core_test$..."]}}))))
