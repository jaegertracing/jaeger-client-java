Changes by Version
==================

0.27.0 (2018-04-18)
-------------------

- Enable development and dependency versions to be overridden from the commandline (#397) <Gary Brown>
- Add tests for metrics exposed via prometheus (#386)  <Kevin Earls>
- Change groupid to io.jaegertracing and remove instrumentations (#375). Sources with com.uber.jaeger have been moved to https://github.com/jaegertracing/legacy-client-java <Pavol Loffay>
- Remove reference to JAEGER_DISABLE_GLOBAL_TRACER (#379) <Charlie Sharpsteen>
- Avoid direct access to apache thrift from jaeger-core via transitive dependency through jaeger-thrift (#374) <Gary Brown>
- Make some methods in HttpSender.Builder public (#376) <Pavol Loffay>

0.26.0 (2018-03-19)
-------------------

- Avoid host lookups if trace tags have already been provided (#371) <Christopher Hunt>
- Update the ReporterConfig to set Sender Correctly (#370) <Nate Hart>
- Fix for issue 366, fix deprecated code in example (#367) <Kevin Earls>

0.25.0 (2018-03-07)
-------------------

- Consolidate codec exception handling and logs exceptions (#362) <Tomasz Adamski>
- Deprecated StatsReporter (#349) <Juraci Paixão Kröhling>
- Ignores B3 headers if invalid values are provided (#355) <Juraci Paixão Kröhling>
- Refactor public constructor API (#346) <Pavol Loffay>
- Expand exception logs (#168) <Pavol Loffay>
- Deprecate JerseyServerFilter#JerseyServerFilter(Tracer, TraceContext) (#352) <Debosmit Ray>
- Micrometer support (#335) <Juraci Paixão Kröhling>
- Metrics are now consistent with Go (#335) <Juraci Paixão Kröhling>
- Updated Trace Resolver (#347) <Gary Brown>


0.24.0 (2018-01-31)
-------------------

- Remove apache instrumentation dependence on jaeger-context (#320) <Debosmit Ray>
- Remove JAX-RS2 instrumentation dependence on TracingUtils/jaeger-context (#319) <Debosmit Ray>
- Fix ClassCastException when a wrapped span impl is activated instead of the Jaeger Span (#327) <Gary Brown>
- Remove shadow dependency from jaeger-b3 (#309) <Prithvi Raj>
- Adding annotated filter priorities for Jersey filters (#329) <Debosmit Ray>
- Handle baggage null values better (#308) <Babak Mozaffari>


0.23.0 (2018-01-12)
-------------------

- Upgrade to OpenTracing API v0.31.0 (#313)


0.22.0-RC3 (2017-12-08)

- Remove dependency on GlobalTracer (#302) <Debosmit Ray>
- Add configuration support for B3 trace context propagation (#296) <Gary Brown>
- Prevent Span from being reported twice on multiple calls to finish() (#299) <Debosmit Ray>
- Do not store client span in thread-local in apache and jax-rs2 instrumentation (#297) <Debosmit Ray>
- Add auth options to HttpSender (#285) <Juraci Paixão Kröhling>


0.22.0-RC2 (2017-10-26)
-----------------------

- Break apart functionality of TracingRequestInterceptor (https://github.com/jaegertracing/jaeger-client-java/pull/248)
- Split out the jaxrs2 filters (https://github.com/jaegertracing/jaeger-client-java/pull/273)
- Add TraceContext backed by ActiveSpanSource (https://github.com/jaegertracing/jaeger-client-java/pull/266)
- Update to Apache License v. 2 (https://github.com/jaegertracing/jaeger-client-java/pull/262)
- Use io.opentracing.GlobalTracer instead of singleton in Configuration (https://github.com/jaegertracing/jaeger-client-java/pull/255)
- Tracer implements Closeable (https://github.com/jaegertracing/jaeger-client-java/pull/242)
- Fix: span builder should accept null in asChildOf (https://github.com/jaegertracing/jaeger-client-java/pull/243)


0.21.0 (2017-09-15)
-------------------
- Support baggage restrictions (https://github.com/uber/jaeger-client-java/pull/217, https://github.com/uber/jaeger-client-java/pull/229)
- Fix concurrency bug when using multiple senders (https://github.com/uber/jaeger-client-java/pull/233)
- Support span log fields in zipkin sender (https://github.com/uber/jaeger-client-java/pull/226)


0.20.6 (2017-08-01)
-------------------
- Use okhttp for HTTP sender (https://github.com/uber/jaeger-client-java/pull/224)


0.20.0 (2017-06-23)
-------------------
- Upgrade to OpenTracing Java 0.30.0 with in-process propagation support (https://github.com/uber/jaeger-client-java/pull/188)
- Add Process IP to jaeger.thrift (https://github.com/uber/jaeger-client-java/pull/197)
- Implement HTTP sender for jaeger.thrift (https://github.com/uber/jaeger-client-java/pull/194)


0.19.0 (2017-05-29)
-------------------
- TraceResolver support (https://github.com/uber/jaeger-client-java/pull/175)
- Do not truncate reported strings (https://github.com/uber/jaeger-client-java/pull/174)
- Bump OpenTracing version to 0.22.0 (https://github.com/uber/jaeger-client-java/pull/164)
- Use jaeger.thrift data model (https://github.com/uber/jaeger-client-java/pull/142)
- Enable process level tags to be associated with the tracer (https://github.com/uber/jaeger-client-java/pull/143)


0.18.0 (2017-04-13)
-------------------

- Add adaptive sampler
- Run RemoteReporter's flush timer in daemon thread (https://github.com/uber/jaeger-client-java/pull/124)
- Bump opentracingVersion to 0.21.0 (https://github.com/uber/jaeger-client-java/pull/137)
- Add Java 6 compatibility (https://github.com/uber/jaeger-client-java/pull/132)


0.17.0 (2016-12-02)
-------------------

- Fix bug where server spans were incorrectly marked as local components
- Tighten checks for marking a span as a RPC


0.16.0 (2016-11-28)
-------------------

- Add a Configuration#closeTracer() method
- Add Builder to TextMapCodec
- Bump up zipkin to 1.16.2
- Automatically add span names for jersey instrumentation


0.15.0 (2016-10-26)
-------------------

- Add init logging
- Add a Tracer.close() method
- Fill in `Host` on all annotations when reporting to Zipkin
- Downgrade to libthrift 0.9.2
- Publish a shaded jar with libthrift 0.9.2 packaged
- Bump up zipkin versions


0.14.0 (2016-10-06)
-------------------

- Fix http url tag on jaxrs2 filters
- Add convenience setter for metrics
- Expose interceptor callbacks and custom operation name for extension classes


0.13.0 (2016-09-29)
-------------------

- Fix bug with dropwizard config parsing


0.12.0 (2016-09-27)
-------------------

- Support Apache HttpClient


0.11.0 (2016-09-22)
-------------------

- fixed crossdock notImplementedError propagation
- refactored configuration
- added google-formatter


0.10.0 (2016-09-16)
-------------------

- Support debug traces via HTTP header "jaeger-debug-id"


0.9.0 (2016-09-12)
-------------------

- Introduce Clock interface for time measurements
- Use nano-time for measuring duration
- Remove powermock dependency
- Upgrade to OT 0.15


0.8.2 (2016-09-09)
-------------------

- Exclude test classes from published jars


0.8.1 (2016-09-09)
-------------------

- Default peer service name to blank as it is required in Thrift Endpoint (fixes #45)


0.8.0 (2016-09-06)
-------------------

- Add jaeger-zipkin module that allows on the wire propagation with B3-xxx headers and uses zipkin-reporter
- Report sampling strategy as root span tags `sampler.type` and `sampler.param`. In case of probabilistic sampling (most frequently used strategy), the values would be `probabilistic` and the sampling probability [0 .. 1], respectively.
- Record host name as `jaeger.hostname` tag on the first-in-process spans (i.e. root spans and rpc-server spans)
- Record the version of the Jaeger library as `jaeger.version` tag


0.7.1 (2016-08-22)
-------------------

- Reduce the size of published jars


0.7.0 (2016-08-19)
-------------------

- Upgrade to OpenTracing API 0.12, with immutable SpanContext

