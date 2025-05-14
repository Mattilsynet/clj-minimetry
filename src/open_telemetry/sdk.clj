(ns open-telemetry.sdk
  (:import (io.opentelemetry.api.baggage.propagation W3CBaggagePropagator)
           (io.opentelemetry.api.common Attributes AttributesBuilder AttributeKey)
           (io.opentelemetry.api.trace.propagation W3CTraceContextPropagator)
           (io.opentelemetry.context.propagation ContextPropagators TextMapPropagator)
           (io.opentelemetry.instrumentation.resources ContainerResource
                                                       HostResource
                                                       OsResource
                                                       ProcessResource
                                                       ProcessRuntimeResource)
           (io.opentelemetry.sdk OpenTelemetrySdk)
           (io.opentelemetry.sdk.logs SdkLoggerProvider)
           (io.opentelemetry.sdk.metrics SdkMeterProvider)
           (io.opentelemetry.sdk.resources Resource)
           (io.opentelemetry.sdk.trace SdkTracerProvider)
           (io.opentelemetry.semconv ServiceAttributes)))

(defn build-resource [service-name service-version]
  (-> (Resource/getDefault)
      .toBuilder
      (.put ServiceAttributes/SERVICE_NAME service-name)
      (.put ServiceAttributes/SERVICE_VERSION service-version)
      .build))

(defn build-attributes [m]
  (let [attrs ^AttributesBuilder (Attributes/builder)]
    (doseq [[k v] m]
      (.put attrs (AttributeKey/stringKey
                   (cond
                     (keyword? k)
                     (str (when-let [ns (namespace k)]
                            (str ns "."))
                          (name k))

                     (string? k)
                     k

                     :else
                     (str k))) v))
    (.build attrs)))

(defn add-attributes [^Resource resource m]
  (.merge resource (Resource/create (build-attributes m))))

(defn embellish-resource [^Resource resource]
  (-> resource
      (.merge (ContainerResource/get))
      (.merge (HostResource/get))
      (.merge (OsResource/get))
      (.merge (ProcessResource/get))
      (.merge (ProcessRuntimeResource/get))))

(defn build-tracer-provider [span-processor resource]
  (-> (SdkTracerProvider/builder)
      (.addSpanProcessor span-processor)
      (.setResource resource)
      .build))

(defn build-meter-provider [metric-reader resource]
  (-> (SdkMeterProvider/builder)
      (.registerMetricReader metric-reader)
      (.setResource resource)
      .build))

(defn build-logger-provider [log-processor resource]
  (-> (SdkLoggerProvider/builder)
      (.addLogRecordProcessor log-processor)
      (.setResource resource)
      .build))

(defn ^:export init-open-telemetry [{:keys [tracer-provider
                                            meter-provider
                                            logger-provider]}]
  (cond-> (OpenTelemetrySdk/builder)
    tracer-provider (.setTracerProvider tracer-provider)
    meter-provider (.setMeterProvider meter-provider)
    logger-provider (.setLoggerProvider logger-provider)
    :always (.setPropagators (ContextPropagators/create
                              (TextMapPropagator/composite
                               ^TextMapPropagator/1
                               (into-array
                                TextMapPropagator
                                [(W3CTraceContextPropagator/getInstance)
                                 (W3CBaggagePropagator/getInstance)]))))
    :then .buildAndRegisterGlobal))

(defn get-noop-instance []
  (.build (OpenTelemetrySdk/builder)))
