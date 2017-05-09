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
