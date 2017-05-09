# Jaeger Tracer Resolver

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

Property | Default | Description
--- | --- | ---
JAEGER_SERVICE_NAME | _none_ | The service name (must be defined)
JAEGER_AGENT_UDP_MAX_PACKET_SIZE | 65000 | The maximum packet size when communicating with the agent via UDP
JAEGER_AGENT_UDP_HOST | localhost | The hostname for communicating with agent via UDP
JAEGER_AGENT_UDP_PORT | 6831 | The port for communicating with agent via UDP
JAEGER_REPORTER_MAX_QUEUE_SIZE | 1000 | The reporter's maximum queue size
JAEGER_REPORTER_FLUSH_INTERVAL | 500 | The reporter's flush interval (ms)

