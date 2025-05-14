(ns open-telemetry.tracing-test
  (:require [clojure.test :refer [deftest is testing]]
            [open-telemetry.tracing :as tracing]))

(deftest get-span-data-test
  (is (= (tracing/get-span-data "Launching rockets")
         {:span/name "Launching rockets"}))

  (is (= (tracing/get-span-data ["Launching rockets"])
         {:span/name "Launching rockets"}))

  (is (= (tracing/get-span-data ["Launching rockets" {:rocket "Yes"}])
         {:span/name "Launching rockets"
          :span/attrs {:rocket "Yes"}}))

  (is (= (tracing/get-span-data :launching-rockets)
         {:span/name "launching-rockets"}))

  (is (= (tracing/get-span-data [:space/launching-rockets])
         {:span/name "space/launching-rockets"}))

  (is (= (tracing/get-span-data {:span/name :space/launching-rockets})
         {:span/name "space/launching-rockets"}))

  (is (= (tracing/get-span-data [:launching-rockets {:rocket "Yes"}])
         {:span/name "launching-rockets"
          :span/attrs {:rocket "Yes"}})))

(deftest get-http-header-attrs-test
  (testing "Flattens http headers"
    (is (= (tracing/get-http-header-attrs
            {"accept" "*/*"
             "host" "localhost:5555"
             "user-agent" "curl/8.6.0"}
            "http.request.header")
           {:otel.semconv/http.request.header.accept ["*/*"]
            :otel.semconv/http.request.header.host ["localhost:5555"]
            :otel.semconv/http.request.header.user-agent ["curl/8.6.0"]})))

  (testing "Splits headers with multiple values"
    ;; Header values cannot contain line breaks. Ring uses line breaks to
    ;; indicate multiple values for the same header.
    (is (= (tracing/get-http-header-attrs
            {"accept" "*/*"
             "host" "localhost:5555"
             "lol" "42\n666"
             "user-agent" "curl/8.6.0"}
            "http.request.header")
           {:otel.semconv/http.request.header.accept ["*/*"]
            :otel.semconv/http.request.header.host ["localhost:5555"]
            :otel.semconv/http.request.header.lol ["42" "666"]
            :otel.semconv/http.request.header.user-agent ["curl/8.6.0"]}))))

(deftest get-http-request-attrs-test
  (testing "Extracts standard otel attributes from http request"
    (is (= (tracing/get-http-request-attrs
            {:body nil
             :character-encoding "utf8"
             :content-length 0
             :content-type nil
             :headers
             {"accept" "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
              "accept-encoding" "gzip, deflate, br, zstd"
              "accept-language" "en-US,en;q=0.9"
              "cache-control" "max-age=0"
              "connection" "keep-alive"
              "host" "localhost:5555"
              "user-agent" "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"},
             :query-string "lol=haha"
             :remote-addr "0:0:0:0:0:0:0:1"
             :request-method :get
             :scheme :http
             :server-name "localhost"
             :server-port 5555
             :start-time 1352322252636625
             :uri "/"
             :websocket? false})
           {:otel.semconv/http.request.method "GET"
            :otel.semconv/url.scheme "http"
            :otel.semconv/url.full "/?lol=haha"
            :otel.semconv/server.address "localhost"
            :otel.semconv/server.port 5555
            :otel.semconv/user_agent.original "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            :otel.semconv/http.request.header.accept ["text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"]
            :otel.semconv/http.request.header.accept-encoding ["gzip, deflate, br, zstd"]
            :otel.semconv/http.request.header.accept-language ["en-US,en;q=0.9"]
            :otel.semconv/http.request.header.cache-control ["max-age=0"]
            :otel.semconv/http.request.header.connection ["keep-alive"]
            :otel.semconv/http.request.header.host ["localhost:5555"]}))))

(deftest get-http-response-attrs-test
  (testing "Returns reasonable defaults for empty response"
    (is (= (tracing/get-http-response-attrs {})
           {:otel.semconv/http.response.status_code 200})))

  (testing "Converts response map"
    (is (= (tracing/get-http-response-attrs
            {:status 201
             :headers {"content-type" "text/html"}
             :cookies {"session"
                       {:value "waw.8c9217be-5f12-4858-ac5d-191c4214e243"
                        :http-only true
                        :same-site :strict
                        :secure true
                        :path "/"}}})
           {:otel.semconv/http.response.status_code 201
            :otel.semconv/http.response.header.content-type ["text/html"]
            "http.response.cookies.session.value" "waw.8c9217be-5f12-4858-ac5d-191c4214e243"
            "http.response.cookies.session.http-only" true
            "http.response.cookies.session.same-site" :strict
            "http.response.cookies.session.secure" true
            "http.response.cookies.session.path" "/"}))))
