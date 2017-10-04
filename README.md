[![Build Status][ci-img]][ci] [![Coverage Status][cov-img]][cov] [![Released Version][maven-img]][maven]

# Jaeger's Tracing Instrumentation Library for Java

 * Intended to be used with [Jaeger](https://github.com/uber/jaeger) backend, but can also be configured to send traces to Zipkin.
 * Implement [Java OpenTracing API](https://github.com/opentracing/opentracing-java).
 * Supports Java 1.6 and above

## Core Modules

Click through for more detailed docs on specific modules.

 * [jaeger-core](./jaeger-core): the core implementation of the OpenTracing API
 * [jaeger-context](./jaeger-context): in-process context propagation
 
## Add-on Modules

 * [jaeger-dropwizard](./jaeger-dropwizard): a feature to initialize Jaeger from [Dropwizard](http://www.dropwizard.io/) apps (including binding to stats/metrics) 
 * [jaeger-apachehttpclient](./jaeger-apachehttpclient): instrumentation for apache http clients
 * [jaeger-jaxrs2](./jaeger-jaxrs2): instrumentation for JAXRS2 filters
 * [jaeger-zipkin](./jaeger-zipkin): compatibility layer for using Jaeger tracer as Zipkin-compatible implementation

## Importing Dependencies
All artifacts are published to Maven Central. 
Snapshot artifacts are also published to
[Sonatype](https://oss.sonatype.org/content/repositories/snapshots/com/uber/jaeger/).
Follow these [instructions](http://stackoverflow.com/questions/7715321/how-to-download-snapshot-version-from-maven-snapshot-repository)
to add the snapshot repository to your build system. 

Add the required dependencies to your project. Usually, this would only be the add-ons you require.
**Please use the latest version:** [![Released Version][maven-img]][maven]

For e.g, to depend on the core jaeger library, you'd include the following
```xml
<dependency>
    <groupId>com.uber.jaeger</groupId>
    <artifactId>jaeger-core</artifactId>
    <version>$jaegerVersion</version>
</dependency>
```

### Thrift version conflicts
Jaeger client uses `org.apache.thrift:libthrift:0.9.2`. If your project depends on a different
version of `libthrift`, it is recommended that you use the shaded `jaeger-thrift` jar we publish
which packages it's own `libthrift`.

To depend on the shaded jar, add the following to your maven build.
Note that this is only supported for a jaeger version >= 0.15.0
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.uber.jaeger</groupId>
      <artifactId>jaeger-thrift</artifactId>
      <classifier>thrift92</classifier>
      <version>$jaegerVersion</version>
    </dependency>
  </dependencies>
</dependencyManagement>
```

## Instantiating the Tracer

Please see [jaeger-core/README](./jaeger-core/README.md).

## In-process Context Propagation
`jaeger-context` defines
[ThreadLocalTraceContext](./jaeger-context/src/main/java/com/uber/jaeger/context)
implementation of `TraceContext` that can be used for propagating the current tracing `Span`
throughout the application without changing the application's code. However, if the application
is starting new threads or is using thread pools, the thread-local context is not going to be
carried over into the execution in the next thread. To maintain context propagation,
a wrapper `TracedExecutorService` is provided that automatically transfers the context
onto the new threads.

```java
ExecutorService instrumentedExecutorService = TracingUtils.tracedExecutor(wrappedExecutorService);
```

## Testing

When testing tracing instrumentation it is often useful to make sure
that all spans are being captured, which is not the case in production
configurations where heavy sampling is applied by default.
The following configuration can be provided to affect which sampling
is applied to the new traces:

```yaml
sampler:
   type: const # can either be const, probabilistic, or ratelimiting
   param: 1  # can either be an integer, a double, or an integer
```

The valid values for `type` are: 
 * `const`: configures a sampler that always makes the same decision
    for new traces depending on the `param`: always no for `param=0`,
    always yes otherwise.
 * `probabilistic`: configures a sampler that samples traces with
    probability equal to `param` (must be between `0.0` and `1.0`)
 * `ratelimiting`: configures a samlper that samples traces with a
    certain rate per second equal to `param`

### Debug Traces (Forced Sampling)

#### Programmatically

The OpenTracing API defines a `sampling.priority` standard tag that
can be used to affect the sampling of a span and its children:

```java
import io.opentracing.tag.Tags;

Tags.SAMPLING_PRIORITY.set(span, 1);
```

#### Via HTTP Headers

Jaeger Tracer also understands a special HTTP Header `jaeger-debug-id`,
which can be set in the incoming request, e.g.

```sh
curl -H "jaeger-debug-id: some-correlation-id" http://myhost.com
```

When Jaeger sees this header in the request that otherwise has no
tracing context, it ensures that the new trace started for this
request will be sampled in the "debug" mode (meaning it should survive
all downsampling that might happen in the collection pipeline), and
the root span will have a tag as if this statement was executed:

```java
span.setTag("jaeger-debug-id", "some-correlation-id")
```

This allows using Jaeger UI to find the trace by this tag.

## Developing

 1. `git submodule update --init`
 2. `./gradlew clean check`
 
### Code Style

This project uses [google java style](https://google.github.io/styleguide/javaguide.html) configured 
via [checkstyle/google_checks.xml](https://github.com/checkstyle/checkstyle/blob/master/src/main/resources/google_checks.xml)

You can also setup the [IntelliJ plugin](https://plugins.jetbrains.com/plugin/8527)
to reformat code from within your IDE or import code style settings from 
[google/intellij-java-google-style](https://github.com/google/styleguide/blob/gh-pages/intellij-java-google-style.xml)

### Lombok
This project uses [Lombok](https://projectlombok.org/) to reduce boilerplate. You can setup
 the [IntelliJ plugin](https://plugins.jetbrains.com/plugin/6317) to add IDE support. 

## License
  
  [The MIT License](LICENSE).



  [ci-img]: https://travis-ci.org/jaegertracing/jaeger-client-java.svg?branch=master
  [ci]: https://travis-ci.org/jaegertracing/jaeger-client-java
  [cov-img]: https://codecov.io/github/jaegertracing/jaeger-client-java/coverage.png?branch=master
  [cov]: https://codecov.io/github/jaegertracing/jaeger-client-java/
  [maven-img]: https://img.shields.io/maven-central/v/com.uber.jaeger/jaeger-core.svg?maxAge=2000
  [maven]: http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.uber.jaeger%22
