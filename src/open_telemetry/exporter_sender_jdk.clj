(ns open-telemetry.exporter-sender-jdk
  (:import (io.opentelemetry.exporter.otlp.logs OtlpGrpcLogRecordExporter)
           (io.opentelemetry.exporter.otlp.metrics OtlpGrpcMetricExporter)
           (io.opentelemetry.exporter.otlp.trace OtlpGrpcSpanExporter)
           (io.opentelemetry.sdk.logs.export BatchLogRecordProcessor)
           (io.opentelemetry.sdk.metrics.export PeriodicMetricReader)
           (io.opentelemetry.sdk.trace.export BatchSpanProcessor)))

(defn ^:export build-span-processor []
  (-> (.build (OtlpGrpcSpanExporter/builder))
      BatchSpanProcessor/builder
      .build))

(defn ^:export build-metric-reader []
  (-> (.build (OtlpGrpcMetricExporter/builder))
      (PeriodicMetricReader/builder)
      .build))

(defn ^:export build-log-processor []
  (-> (.build (OtlpGrpcLogRecordExporter/builder))
      (BatchLogRecordProcessor/builder)
      .build))
