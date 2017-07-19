# Core
This module provides core tracing functionality for custom instrumentation. 

## Maven coordinates
```xml
<dependency>
    <groupId>com.uber.jaeger</groupId>
    <artifactId>jaeger-core</artifactId>
    <version>$jaegerVersion</version>
</dependency>
```

## Usage

### Production

For production usage, it is recommended to use `com.uber.jaeger.Configuration` with default parameters:

```java
Configuration config = new Configuration("myServiceName", null, null);
config.setStatsFactory(...); // optional if you want to get metrics about tracer behavior

Tracer tracer = config.getTracer();
```

The `config` objects lazily builds and configures Jaeger Tracer. Multiple calls to `getTracer()` return the same instance.


#### Configuration via Environment

It is also possible to obtain a `com.uber.jaeger.Configuration` object configured using properties specified
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
JAEGER_REPORTER_LOG_SPANS | no | Whether the reporter should also log the spans
JAEGER_REPORTER_MAX_QUEUE_SIZE | no | The reporter's maximum queue size
JAEGER_REPORTER_FLUSH_INTERVAL | no | The reporter's flush interval (ms)
JAEGER_SAMPLER_TYPE | no | The sampler type
JAEGER_SAMPLER_PARAM | no | The sampler parameter (number)
JAEGER_SAMPLER_MANAGER_HOST_PORT | no | The host name and port when using the remote controlled sampler
JAEGER_TAGS | no | A comma separated list of `name = value` tracer level tags, which get added to all reported spans. The value can also refer to an environment variable using the format `${envVarName:default}`, where the `:default` is optional, and identifies a value to be used if the environment variable cannot be found


#### Obtaining Tracer via TracerResolver

Jaeger's Java Client also provides an implementation of the
[TracerResolver](https://github.com/opentracing-contrib/java-tracerresolver), allowing a `Tracer` to be
obtained in a vendor neutral manner. This `TracerResolver` implementation uses the configuration via
environment approach described above.

More information about using the `TracerResolver` can be found [here](../jaeger-tracerresolver/README.md).


### Development

The last two parameters to `new Configuration()` allow control over configuration of the Sampler and Reporter.
However, especially in unit tests, it's useful to have tracer that is not connected to tracing backend, but collects
spans in memory:

```java
Reporter reporter = new InMemoryReporter();
Sampler sampler = new ConstSampler(true);
Tracer tracer = new Tracer.Builder(serviceName, reporter, sampler).build();
```

See also: [opentracing-java](https://github.com/opentracing/opentracing-java)
