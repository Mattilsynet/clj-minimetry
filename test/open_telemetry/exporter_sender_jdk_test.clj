(ns open-telemetry.exporter-sender-jdk-test
  (:require [clojure.test :refer [deftest is testing]]
            [open-telemetry.exporter-sender-jdk :as exporter-sender-jdk]))

(defn infer-endpoint
  "Guess endpoint from a to-stringed java object

  Endpoint is not accessible via private or public properties on
  OtlpGrpcSpanExporter objects - "
  [java-object-str]
  (some-> (re-find #"endpoint=([a-z:/\.0-9-]+)[,}]" java-object-str) second))

(deftest infer-endpoint-test
  (testing "more stuff after endpoint"
    (is (= (infer-endpoint "... type=span, endpoint=https://teod.eu/test:10101, ...")
           "https://teod.eu/test:10101")))
  (testing "endpoint is the last attribute"
    (is (= (infer-endpoint "BatchSpanProcessor{spanExporter=OtlpGrpcSpanExporter{endpoint=http://localhost:4317}}")
           "http://localhost:4317"))))

(deftest endpoint
  (doseq [builder-fn [exporter-sender-jdk/build-span-processor
                      exporter-sender-jdk/build-metric-reader
                      exporter-sender-jdk/build-log-processor]]
    (testing "Defaults to the default GRPC port on localhost"
      (is (= (infer-endpoint (str (builder-fn)))
             "http://localhost:4317")))
    (testing "... but can be overridden."
      (is (= (infer-endpoint (str (builder-fn {:endpoint "http://the-endpoint:1234"})))
             "http://the-endpoint:1234")))))
