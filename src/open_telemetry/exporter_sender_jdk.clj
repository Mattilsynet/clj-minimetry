(ns open-telemetry.exporter-sender-jdk
  (:import (io.opentelemetry.exporter.otlp.logs OtlpGrpcLogRecordExporter)
           (io.opentelemetry.exporter.otlp.metrics OtlpGrpcMetricExporter)
           (io.opentelemetry.exporter.otlp.trace OtlpGrpcSpanExporter)
           (io.opentelemetry.sdk.logs.export BatchLogRecordProcessor)
           (io.opentelemetry.sdk.metrics.export PeriodicMetricReader)
           (io.opentelemetry.sdk.trace.export BatchSpanProcessor)))

(set! *warn-on-reflection* true)

(defn ^:export build-span-processor
  ([] (build-span-processor {}))
  ([{:keys [endpoint]}]
   (-> (cond-> (OtlpGrpcSpanExporter/builder)
         endpoint (.setEndpoint endpoint))
       .build
       BatchSpanProcessor/builder
       .build)))

(defn ^:export build-metric-reader
  ([] (build-metric-reader {}))
  ([{:keys [endpoint]}]
   (-> (cond-> (OtlpGrpcMetricExporter/builder)
         endpoint (.setEndpoint endpoint))
       .build
       PeriodicMetricReader/builder
       .build)))

(defn ^:export build-log-processor
  ([] (build-log-processor {}))
  ([{:keys [endpoint]}]
   (-> (cond-> (OtlpGrpcLogRecordExporter/builder)
         endpoint (.setEndpoint endpoint))
       .build
       BatchLogRecordProcessor/builder
       .build)))
