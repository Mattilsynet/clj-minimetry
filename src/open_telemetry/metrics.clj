(ns open-telemetry.metrics
  (:require [open-telemetry.core :as otel])
  (:import (io.opentelemetry.api.metrics DoubleGauge LongCounter Meter)))

(defn get-meter [meter-name]
  (.getMeter (otel/get-instance) meter-name))

(defn ^:export create-counter [{:otel.metric/keys [name description unit]}]
  (cond-> (.counterBuilder ^Meter (get-meter (namespace name)) (str (namespace name) "." (clojure.core/name name)))
    description (.setDescription description)
    unit (.setUnit unit)
    :then .build))

(defn ^:export inc-counter [^LongCounter counter v & [attrs]]
  (.add counter ^long v (otel/build-attributes attrs)))

(defn ^:export create-gauge [{:otel.metric/keys [name description unit]}]
  (cond-> (.gaugeBuilder ^Meter (get-meter (namespace name)) (str (namespace name) "." (clojure.core/name name)))
    description (.setDescription description)
    unit (.setUnit unit)
    :then .build))

(defn ^:export set-gauge [^DoubleGauge gauge v & [attrs]]
  (.set gauge ^double v (otel/build-attributes attrs)))
