# Jaeger Tracer Resolver

This module provides a Jaeger implementation for the [TracerResolver](https://github.com/opentracing-contrib/java-tracerresolver). This mechanism provides a vendor neutral approach for obtaining a `Tracer` using the JDK
`ServiceLoader`.


## Maven Dependency
```xml
<dependency>
    <groupId>com.uber.jaeger</groupId>
    <artifactId>jaeger-tracerresolver</artifactId>
    <version>$jaegerVersion</version>
</dependency>
```

## Usage

```java
Tracer tracer = TracerResolver.resolveTracer();
```

## Configuration Options

The following configuration properties can be provided either as an environment variable or system property.
The system property will override an environment variable for the same property name.

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

