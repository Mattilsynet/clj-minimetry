(ns open-telemetry.core
  (:import (io.opentelemetry.api OpenTelemetry GlobalOpenTelemetry)
           (io.opentelemetry.api.common Attributes AttributesBuilder))
  (:require [clojure.string :as str]
            [open-telemetry.sdk :as otel-sdk]))

(def ^:dynamic *open-telemetry* nil)

(defn set-instance! [open-telemetry]
  (alter-var-root #'*open-telemetry* (constantly open-telemetry)))

(defn ^:export unset-instance! []
  (when *open-telemetry*
    (alter-var-root #'*open-telemetry* (constantly nil))
    (GlobalOpenTelemetry/resetForTest)))

(defn get-instance ^OpenTelemetry []
  (or *open-telemetry* (otel-sdk/get-noop-instance)))

(defn ->attr-k [k]
  (cond
    (string? k) k

    (keyword? k)
    (if (#{"otel.semconv"} (namespace k))
      (name k)
      (str (when-let [ns (namespace k)]
             (str ns ".")) (name k)))

    :else (pr-str k)))

(defn build-attributes ^Attributes [m]
  (let [builder ^AttributesBuilder (Attributes/builder)]
    (doseq [[k v] m]
      (cond
        (boolean? v)
        (.put ^AttributesBuilder builder ^String (->attr-k k) ^boolean v)

        (string? v)
        (.put ^AttributesBuilder builder ^String (->attr-k k) ^String v)

        (int? v)
        (.put ^AttributesBuilder builder ^String (->attr-k k) ^long (long v))

        (number? v)
        (.put ^AttributesBuilder builder ^String (->attr-k k) ^double (double v))

        (and (coll? v)
             (not (map? v)))
        (cond
          (every? boolean? v)
          (.put ^AttributesBuilder builder ^String (->attr-k k) ^booleans (boolean-array v))

          (every? int? v)
          (.put ^AttributesBuilder builder ^String (->attr-k k) ^longs (long-array v))

          (every? #(or (double? %) (float? %)) v)
          (.put ^AttributesBuilder builder ^String (->attr-k k) ^doubles (double-array v))

          :else
          (.put ^AttributesBuilder builder ^String (->attr-k k) ^String (pr-str v)))

        :else
        (.put ^AttributesBuilder builder ^String (->attr-k k) ^String (pr-str v))))
    (.build builder)))

(defn exception->data [^Exception e]
  (cond-> {:message (.getMessage e)}
    (ex-data e) (assoc :ex-data (ex-data e))
    true (assoc :stacklet (str/join "\n" (vec (take 3 (.getStackTrace e)))))
    (.getCause e) (assoc :cause (exception->data (.getCause e)))))

(defn truncate [v]
  (if (string? v)
    (str (str/join (take 3 v)) "...")
    v))

(defn ^{:indent 1} flatten-data [data & [{:keys [prefix truncate-ks] :as opt}]]
  (let [nil-val (get opt :nil-val "nil")]
    (if (and (nil? data) prefix nil-val)
      {prefix nil-val}
      (let [truncate? (set truncate-ks)]
        (loop [x (if (map? data) data (map vector (range) data))
               res {}]
          (if (empty? x)
            res
            (let [[k v] (first x)
                  v (if v (cond-> v (truncate? k) truncate) nil-val)
                  k (cond->> (if (keyword? k)
                               (name k)
                               (str k))
                      prefix (str prefix "."))]
              (recur (next x)
                     (if (map? v)
                       (into res (flatten-data v (assoc opt :prefix k)))
                       (assoc res k v))))))))))
