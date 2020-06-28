[![Java Docs][javadoc-badge]][javadoc]

# Core
This module provides core tracing functionality for custom instrumentation. 

## Maven coordinates
```xml
<dependency>
    <groupId>io.jaegertracing</groupId>
    <artifactId>jaeger-core</artifactId>
    <version>$jaegerVersion</version>
</dependency>
```

Note that you'll most likely want to include a higher-level dependency that includes components
sending data to a backend, like [`io.jaegertracing:jaeger-thrift`](../jaeger-thrift).

## Usage

### Production

A tracer instance can be obtained via the `io.jaegertracing.Configuration` object. In the general
case, the most appropriate way is to get a tracer that is configured based on environment variables, like:

```java
Tracer tracer = Configuration.fromEnv().getTracer();
```

If a more complex customization is required, the builder pattern can be used, like:

```java
CodecConfiguration codecConfiguration = new CodecConfiguration()
    .withCodec(Builtin.HTTP_HEADERS, codec1)
    .withCodec(Builtin.HTTP_HEADERS, codec2);

Configuration configuration = new Configuration("foo")
    .withCodec(codecConfiguration);

Tracer tracer = configuration.getTracer();
```

Multiple calls to `Configuration#getTracer()` result in the same tracer instance.

For more advanced cases, direct access to `io.jaegertracing.internal.JaegerTracer.Builder` might
be required. Note that this class is not part of the public API and its usage should be avoided.

A tracer instance can be obtained from `JaegerTracer.Builder` like:

```java
Tracer tracer = new JaegerTracer.Builder("myServiceName").build()
```

#### W3C Trace Context propagation
Jaeger Tracer supports [W3C Trace Context](https://w3c.github.io/trace-context/) propagation format.

Example configuration:

```java
w3cCodec = new io.jaegertracing.internal.propagation.TraceContextCodec();
tracer = new JaegerTracer.Builder(serviceName)
  .registerInjector(Format.Builtin.HTTP_HEADERS, w3cCodec)
  .registerExtractor(Format.Builtin.HTTP_HEADERS, w3cCodec)
  ...
```

#### B3 propagation
Jaeger Tracer can also work in the environment where B3 propagation is used. This is mostly related
to systems instrumented with Zipkin. Once you register `B3TextMapCodec`, Jaeger can join traces 
started by other Zipkin instrumented applications. This includes reading headers 
like `X-B3-TraceId`. Jaeger B3 implementation automatically propagates baggage and by default it
uses `baggage-` prefix.

Example configuration:

```java
b3Codec = new io.jaegertracing.internal.propagation.B3TextMapCodec();
tracer = new JaegerTracer.Builder(serviceName)
  .registerInjector(Format.Builtin.HTTP_HEADERS, b3Codec)
  .registerExtractor(Format.Builtin.HTTP_HEADERS, b3Codec)
  ...
```

### Configuration via Environment

When obtaining a tracer instance using the `io.jaegertracing.Configuration#fromEnv()` method, values specified
via system properties (`-DJAEGER_SERVICE_NAME=foo`) will override values specified via environment variables.

The following property names are available:

Property | Required | Description
--- | --- | ---
JAEGER_SERVICE_NAME | yes | The service name
JAEGER_AGENT_HOST | no | The hostname for communicating with agent via UDP
JAEGER_AGENT_PORT | no | The port for communicating with agent via UDP
JAEGER_ENDPOINT | no | The traces endpoint, in case the client should connect directly to the Collector, like http://jaeger-collector:14268/api/traces
JAEGER_AUTH_TOKEN | no | Authentication Token to send as "Bearer" to the endpoint
JAEGER_USER | no | Username to send as part of "Basic" authentication to the endpoint
JAEGER_PASSWORD | no | Password to send as part of "Basic" authentication to the endpoint
JAEGER_PROPAGATION | no | Comma separated list of formats to use for propagating the trace context. Defaults to the standard Jaeger format. Valid values are **jaeger** and **b3**
JAEGER_REPORTER_LOG_SPANS | no | Whether the reporter should also log the spans
JAEGER_REPORTER_MAX_QUEUE_SIZE | no | The reporter's maximum queue size
JAEGER_REPORTER_FLUSH_INTERVAL | no | The reporter's flush interval (ms)
JAEGER_SAMPLER_TYPE | no | The [sampler type](https://www.jaegertracing.io/docs/latest/sampling/#client-sampling-configuration)
JAEGER_SAMPLER_PARAM | no | The sampler parameter (number)
JAEGER_SAMPLER_MANAGER_HOST_PORT | no | The host name and port when using the remote controlled sampler
JAEGER_TAGS | no | A comma separated list of `name = value` tracer level tags, which get added to all reported spans. The value can also refer to an environment variable using the format `${envVarName:default}`, where the `:default` is optional, and identifies a value to be used if the environment variable cannot be found

Setting `JAEGER_AGENT_HOST`/`JAEGER_AGENT_PORT` will make the client send traces to the agent via `UdpSender`.
If the `JAEGER_ENDPOINT` environment variable is also set, the traces are sent to the endpoint, effectively making
the `JAEGER_AGENT_*` vars ineffective.

When the `JAEGER_ENDPOINT` is set, the `HttpSender` is used when submitting traces to a remote
endpoint, usually served by a Jaeger Collector. If the endpoint is secured, a HTTP Basic Authentication
can be performed by setting the related environment vars. Similarly, if the endpoint expects an authentication
token, like a JWT, set the `JAEGER_AUTH_TOKEN` environment variable. If the Basic Authentication environment
variables *and* the Auth Token environment variable are set, Basic Authentication is used.
 
### Obtaining Tracer via TracerResolver

Jaeger's Java Client also provides an implementation of the
[TracerResolver](https://github.com/opentracing-contrib/java-tracerresolver), allowing a `Tracer` to be
obtained in a vendor neutral manner. This `TracerResolver` implementation uses the configuration via
environment approach described above.

More information about using the `TracerResolver` can be found [here](../jaeger-tracerresolver/README.md).

### Reporting internal metrics via Micrometer

The Jaeger Java Client collects *internal* metrics and is able to report them via [Micrometer](http://micrometer.io).
To accomplish that, include the artifact `io.jaegertracing:jaeger-micrometer` as a dependency to your project. The
integration should be done automatically via the service loader.

If you prefer a manual configuration, it can be done with a code similar to this:

```java
MicrometerMetricsFactory metricsReporter = new MicrometerMetricsFactory();
Configuration configuration = new Configuration("myServiceName");
Tracer tracer = configuration
    .getTracerBuilder()
    .withMetricsFactory(metricsReporter)
    .build();
```
### Log Correlation

The Jaeger Java Client also provides log correlation support by configuring the logging context (MDC)
with the following variables:
  
trace id - %{traceId}  
span id  - %{spanId}  
sampled  - %{sampled}  

To accomplish that Jaegar Tracer is created with the following two steps:

1. Create the MDCScopeManager using either default names:

   ```java
    MDCScopeManager scopeManager = new MDCScopeManager.Builder().build()
    ```
    Or by providing optional custom names:
    
    ```java
    MDCScopeManager scopeManager = new MDCScopeManager
                                   .Builder()
                                   .withMDCTraceIdKey("CustomTraceId")
                                   .withMDCSampledKey("customSampled")
                                   .withMDCSpanIdKey("customSpanId")
                                   .build();
    ```
2. Create the Jaegar Tracer by supplying the MDCScopeManager created step 1:
```java
JaegerTracer.Builder("serviceName").withScopeManager(scopeManager).build();	
```

## Development

Especially in unit tests, it's useful to have tracer that is not connected to tracing backend, but collects
spans in memory:

```java
Reporter reporter = new InMemoryReporter();
Sampler sampler = new ConstSampler(true);
Tracer tracer = new JaegerTracer.Builder(serviceName)
  .withReporter(reporter)
  .withSampler(sampler)
  .build();
```

See also: [opentracing-java](https://github.com/opentracing/opentracing-java)

[javadoc]: http://javadoc.io/doc/io.jaegertracing/jaeger-core
[javadoc-badge]: http://javadoc.io/badge/io.jaegertracing/jaeger-core.svg
