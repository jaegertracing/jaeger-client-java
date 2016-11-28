Changes by Version
==================

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

