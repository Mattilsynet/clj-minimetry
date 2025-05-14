(ns open-telemetry.tracing
  (:require [clojure.string :as str]
            [open-telemetry.core :as otel])
  (:import (io.opentelemetry.api OpenTelemetry)
           (io.opentelemetry.api.common Attributes)
           (io.opentelemetry.api.trace Span SpanContext SpanKind StatusCode TraceFlags TraceId TraceState)
           (io.opentelemetry.context Context Scope)))

(def ^:dynamic *current-root-span* nil)
(def ^:dynamic *current-span* nil)
(def ^:dynamic *config* {:print-spans? false})

(defn configure! [{:keys [print-spans?]}]
  (alter-var-root #'*config* (constantly {:print-spans? print-spans?})))

(def span-kinds
  {:span.kind/consumer SpanKind/CONSUMER
   :span.kind/client SpanKind/CLIENT
   :span.kind/server SpanKind/SERVER
   :span.kind/producer SpanKind/PRODUCER
   :span.kind/internal SpanKind/INTERNAL})

(defn set-attributes [^Span span attrs]
  (doseq [[k v] attrs]
    (try
      (cond
        (boolean? v)
        (.setAttribute span ^String (otel/->attr-k k) ^boolean v)

        (string? v)
        (.setAttribute span ^String (otel/->attr-k k) ^String v)

        (int? v)
        (.setAttribute span ^String (otel/->attr-k k) ^long (long v))

        (number? v)
        (.setAttribute span ^String (otel/->attr-k k) ^double (double v))

        (and (coll? v)
             (not (map? v)))
        (.setAttribute span ^String (otel/->attr-k k) ^String (pr-str v))

        :else
        (.setAttribute span ^String (otel/->attr-k k) ^String (pr-str v)))
      (catch Exception e
        (throw (ex-info "Unsupported otel attribute"
                        {:k k
                         :v v
                         :otel/k (otel/->attr-k k)
                         :otel/v v}
                        e))))))

(defn ->span-name [n]
  (cond
    (qualified-keyword? n)
    (str (namespace n) "/" (name n))

    (keyword? n)
    (name n)

    :else n))

(defn get-span-data [opt]
  (cond
    (or (string? opt) (keyword? opt))
    {:span/name (->span-name opt)}

    (map? opt)
    (update opt :span/name ->span-name)

    (vector? opt)
    (let [[span-name attrs] opt]
      (cond-> {:span/name (->span-name span-name)}
        (not-empty attrs) (assoc :span/attrs attrs)))))

(defn make-span [^OpenTelemetry open-telemetry {:keys [ns]} span-data]
  (let [tracer (.getTracer open-telemetry ns)
        thread (Thread/currentThread)
        span-builder (.spanBuilder tracer (:span/name span-data))]
    (when (contains? span-data :span/parent-id)
      (if-let [id (:span/parent-id span-data)]
        (SpanContext/create
         (TraceId/fromLongs (System/currentTimeMillis) (System/nanoTime))
         id
         (TraceFlags/getDefault)
         (TraceState/getDefault))
        (.setParent span-builder (Context/root))))
    (when-let [kind (span-kinds (:span/kind span-data))]
      (.setSpanKind span-builder kind))
    (.startSpan span-builder)
    (doto (.startSpan span-builder)
      (set-attributes
       (merge (cond-> {:otel.semconv/thread.id (.getId thread)
                       :otel.semconv/code.namespace ns}
                (not-empty (.getName thread)) (assoc :otel.semconv/thread.name (.getName thread)))
              (:span/attrs span-data))))))

(defn ^{:export true :style/indent 1} add-event [event-name & [attrs]]
  (if *current-span*
    (try
      (.addEvent ^Span *current-span* ^String event-name ^Attributes (otel/build-attributes attrs))
      (catch Exception e
        (let [attrs (-> (otel/exception->data e)
                        (otel/flatten-data {:prefix "exception"})
                        (assoc :original-event event-name)
                        otel/build-attributes)]
          (.addEvent ^Span *current-span*
                     "Failed to set attributes on event"
                     ^Attributes attrs))))
    (throw (ex-info "Can't add event without *current-span*, call from within `with-span`." {:event-name event-name}))))

(defn ^{:export true} fail!
  ([]
   (if *current-span*
     (fail! *current-span*)
     (throw (Error. "Can't fail without *current-span*, call from within `with-span`."))))
  ([span] (.setStatus ^Span span StatusCode/ERROR)))

(defn ^{:export true :style/indent 1} add-error [event-name & [attrs]]
  (add-event event-name attrs)
  (fail!))

(defn ^:export add-attrs [attrs]
  (if *current-span*
    (try
      (set-attributes *current-span* attrs)
      (catch Exception e
        (add-event "Failed to add attributes to span"
                   (assoc (otel/flatten-data (otel/exception->data e) {:prefix "exception"}) :attrs (pr-str attrs)))))
    (throw (ex-info "Can't add attributes without *current-span*, call from within `with-span`." {:attrs attrs}))))

(defn ^:export add-root-attrs [attrs]
  (when *current-root-span*
    ;; Det _er_ faktisk greit å sette rot-attributter utenfor en span. add-attrs
    ;; er forventet brukt i samme funksjon som kaller with-span, så der er
    ;; manglende current-span ansett som en brukerfeil. add-root-attrs er
    ;; derimot tenkt brukt litt "her og der", uten at du nødvendigvis har helt
    ;; kontroll på hvor span-en bor. Så da må vi nesten se gjennom fingrene på
    ;; et forsøk på å bruke den utenfor en span.
    (try
      (set-attributes *current-root-span* attrs)
      (catch Exception e
        (add-event "Failed to add attributes to root span"
          (assoc (otel/flatten-data (otel/exception->data e) {:prefix "exception"}) :attrs (pr-str attrs)))))))

(defn record-exception
  ([t]
   (if *current-span*
     (record-exception *current-span* t)
     (throw (Error. "Can't record exception without *current-span*, call from within `with-span`."))))
  ([span t]
   (fail! span)
   (.recordException ^Span span t)))

(defn ^:export get-current-trace-id []
  (when *current-span*
    (.getTraceId (.getSpanContext ^Span *current-span*))))

(defmacro ^:export with-span [bindings & body]
  (let [span (with-meta (gensym "span") {:tag `Span})
        scope (with-meta (gensym "scope") {:tag `Scope})]
    `(let [ctx# (assoc ~(meta &form) :file ~*file* :ns ~(str *ns*))
           span-data# (get-span-data ~bindings)
           ~span (make-span (otel/get-instance) ctx# span-data#)
           start-ms# (System/currentTimeMillis)]
       (with-open [~scope (.makeCurrent ~span)]
         (when (:print-spans? *config*)
           (println "Starting" (:span/name span-data#)))
         (try
           (binding [*current-span* ~span
                     *current-root-span* (or *current-root-span* ~span)]
             (let [res# (do ~@body)]
               (when (:print-spans? *config*)
                 (println "Complete" (:span/name span-data#) (str "(" (- (System/currentTimeMillis) start-ms#) "ms)")))
               res#))
           (catch Throwable t#
             (record-exception ~span t#)
             (throw t#))
           (finally
             (.end ~span)))))))

(defmacro ^:export with-root-span [bindings & body]
  `(let [bindings# (assoc (get-span-data ~bindings) :span/parent-id nil)]
     (binding [*current-root-span* nil]
       (with-span bindings#
         ~@body))))

(defn get-http-header-attrs [headers prefix]
  (->> (for [[k v] (otel/flatten-data headers {:prefix prefix
                                               :nil-val nil})]
         [(keyword "otel.semconv" (name k))
          (if (coll? v)
            v
            (str/split (str v) #"\n"))])
       (into {})))

(defn get-http-request-attrs [req]
  (into (get-http-header-attrs (dissoc (:headers req) "user-agent" "authorization") "http.request.header")
        (cond-> {:otel.semconv/http.request.method (str/upper-case (name (:request-method req)))
                 :otel.semconv/server.address (:server-name req)
                 :otel.semconv/server.port (:server-port req)
                 :otel.semconv/url.full (str (:uri req)
                                             (when-let [qs (:query-string req)]
                                               (str "?" qs)))
                 :otel.semconv/url.scheme (some-> req :scheme name)}
          (get (:headers req) "user-agent")
          (assoc :otel.semconv/user_agent.original (get (:headers req) "user-agent")))))

(defn get-http-response-attrs [res]
  (-> (:headers res)
      (get-http-header-attrs "http.response.header")
      (into {:otel.semconv/http.response.status_code (or (:status res) 200)})
      (into (otel/flatten-data (dissoc res :headers :status :body) {:prefix "http.response"}))))

(defmacro defn🕵️‍♂️
  {:clj-kondo/lint-as 'clojure.core/defn}
  [fn-name & forms]
  (let [[docstring arg-list & body] (if (string? (first forms))
                                      forms
                                      (cons "" forms))]
    `(defn ~fn-name
       ~docstring
       ~arg-list
       (with-span [~(str *ns* "/" fn-name)]
         ~@body))))

(defn pmap-with-tracing-context [f coll]
  (let [context (Context/current)]
    (pmap #(do (.makeCurrent context)
               (f %))
          coll)))
