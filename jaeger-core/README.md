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
The simplest way to get a Tracer for development is to use the following snippet. 
For production usage, the recommended path is to instantiate a `com.uber.jaeger.Configuration`
object, and use `getTracer()`.

```java
Reporter reporter = new InMemoryReporter();
Sampler sampler = new ConstSampler(true);
Tracer tracer = new Tracer.Builder(serviceName, reporter, sampler).build();
```

See also: [opentracing-java](https://github.com/opentracing/opentracing-java)
