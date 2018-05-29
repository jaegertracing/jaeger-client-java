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

## Usage

### Production

Tracer can be created via `io.jaegertracing.JaegerTracer.Builder` or `io.jaegertracing.Configuration`.
For production it is recommended to use both classes with default values.

`JaegerTracer.Builder` example:

```java
Tracer tracer = new JaegerTracer.Builder("myServiceName")
  .build()
```

`Configuration` holds only primitive values and it is designed to be used with configuration
files or when configuration is provided in environmental variables.

```java
Configuration config = new Configuration("myServiceName")
  .withReporter(...); // optional if you want to get metrics about tracer behavior

Tracer tracer = config.getTracer();
```

The `config` objects lazily builds and configures Jaeger Tracer. Multiple calls to `getTracer()` return the same instance.

##### B3 propagation
Jaeger tracer can also work in the environment where B3 propagation is used. This is mostly related 
to systems instrumented with Zipkin. Once you register `B3TextMapCodec`, Jaeger can join traces 
started by other Zipkin instrumented applications. This includes reading headers 
like "X-B3-TraceId".

Example configuration:
```java
b3Codec = new B3TextMapCodec();
tracer = new JaegerTracer.Builder(serviceName)
  .registerInjector(Format.Builtin.HTTP_HEADERS, b3Codec)
  .registerExtractor(Format.Builtin.HTTP_HEADERS, b3Codec)
  ...
```

#### Configuration via Environment

It is also possible to obtain a `io.jaegertracing.Configuration` object configured using properties specified
as environment variables or system properties. A value specified as a system property will override a value
specified as an environment variable for the same property name.

```java
Configuration config = Configuration.fromEnv();
```

The property names are:

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
JAEGER_SAMPLER_TYPE | no | The sampler type
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
 
#### Obtaining Tracer via TracerResolver

Jaeger's Java Client also provides an implementation of the
[TracerResolver](https://github.com/opentracing-contrib/java-tracerresolver), allowing a `Tracer` to be
obtained in a vendor neutral manner. This `TracerResolver` implementation uses the configuration via
environment approach described above.

More information about using the `TracerResolver` can be found [here](../jaeger-tracerresolver/README.md).

#### Reporting internal metrics via Micrometer

The Jaeger Java Client collects internal metrics and is able to report them via [Micrometer](http://micrometer.io).
To accomplish that, include the artifact `io.jaegertracing:jaeger-micrometer` as a dependency to your project and use
`MicrometerMetricsFactory` like this:

```java
MicrometerMetricsFactory metricsReporter = new MicrometerMetricsFactory();
Configuration configuration = new Configuration("myServiceName");
Tracer tracer = configuration
  .getTracerBuilder()
  .withMetrics(new io.jaegertracing.metrics.Metrics(metricsReporter))
  .build();
```

### Development

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
