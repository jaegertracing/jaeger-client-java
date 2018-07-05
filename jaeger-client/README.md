[![Released Version][maven-img]][maven]

# Client
This module brings the necessary modules in order for an instrumented application to start
capturing spans. It currently brings:

* `jaeger-core`
* `jaeger-thrift`
* `jaeger-tracerresolver`

With this set, an instrumented application using OpenTracing's TracerResolver can just use
the Jaeger Java Client without any hard code dependency, configuring it via environment variables.

This set might change without warning in the future, so, this module is suitable only for applications
with a simple usage of the Jaeger Tracer. If you require access to classes inside any of those modules,
make sure to add such module as a direct dependency to your module.

## Maven coordinates
```xml
<dependency>
    <groupId>io.jaegertracing</groupId>
    <artifactId>jaeger-client</artifactId>
    <version>$jaegerVersion</version>
</dependency>
```

[maven-img]: https://img.shields.io/maven-central/v/io.jaegertracing/jaeger-client.svg?maxAge=2000
[maven]: http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.jaegertracing%22%20a%3A%22jaeger-client%22
