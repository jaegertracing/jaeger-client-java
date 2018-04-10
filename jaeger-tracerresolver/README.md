# Jaeger Tracer Resolver

This module provides a Jaeger implementation for the [TracerResolver](https://github.com/opentracing-contrib/java-tracerresolver). This mechanism provides a vendor neutral approach for obtaining a `Tracer` using the JDK
`ServiceLoader`.


## Maven Dependency
```xml
<dependency>
    <groupId>io.jaegertracing</groupId>
    <artifactId>jaeger-tracerresolver</artifactId>
    <version>$jaegerVersion</version>
</dependency>
```

## Usage

```java
Tracer tracer = TracerResolver.resolveTracer();
```

This tracer is configured via environment variables (or system properties). The properties are
described [here](../jaeger-core/README.md).
