(ns open-telemetry.stdout
  (:import (io.opentelemetry.exporter.logging LoggingMetricExporter LoggingSpanExporter SystemOutLogRecordExporter)
           (io.opentelemetry.sdk.logs.export BatchLogRecordProcessor)
           (io.opentelemetry.sdk.metrics.export PeriodicMetricReader)
           (io.opentelemetry.sdk.trace.export SimpleSpanProcessor)
           (java.time Duration)))

(defn ^:export build-span-processor []
  (SimpleSpanProcessor/create (LoggingSpanExporter/create)))

(defn ^:export build-metric-exporter []
  (-> (LoggingMetricExporter/create)
      PeriodicMetricReader/builder
      (.setInterval (Duration/parse "PT1S"))
      .build))

(defn ^:export build-log-processor []
  (-> (SystemOutLogRecordExporter/create)
      BatchLogRecordProcessor/builder
      .build))
