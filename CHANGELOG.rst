Changes by Version
==================

0.10.0 (unreleased)
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

