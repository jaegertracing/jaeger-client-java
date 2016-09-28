[![Build Status][ci-img]][ci] [![Coverage Status][cov-img]][cov] [![Released Version][maven-img]][maven]

# Jaeger Bindings for Java OpenTracing API

This is a client side library that implements
[Java OpenTracing API](https://github.com/opentracing/opentracing-java),
with Zipkin-compatible data model.

# Modules #

 * [jaeger-core](./jaeger-core): the core implementation of the OpenTracing API
 * [jaeger-context](./jaeger-context): in-process context propagation
 * [jaeger-jaxrs2](./jaeger-jaxrs2): instrumentation for JAXRS2 filters
 * [jaeger-apachehttpclient](.jaeger-apachehttpclient): instrumentation for apache http clients
 * [jaeger-dropwizard](./jaeger-dropwizard): a feature to initialize Jaeger from [Dropwizard](http://www.dropwizard.io/) apps (including binding to stats/metrics) 
 * [jaeger-zipkin](./jaeger-zipkin): compatibility layer for using Jaeger tracer as Zipkin-compatible implementation

# Usage #

## Importing Dependencies ##
All artifacts are published to Maven Central. 
Snapshot artifacts are also published to
[Sonatype](https://oss.sonatype.org/content/repositories/snapshots/com/uber/jaeger/).
Follow these [instructions](http://stackoverflow.com/questions/7715321/how-to-download-snapshot-version-from-maven-snapshot-repository)
to add the snapshot repository to your build system. 

Add the required dependencies to your project. Usually, this would only be a single dependency.
**Please use the latest version:** [![Released Version][maven-img]][maven].

### Maven Coordinates ###
#### Dropwizard ####
If you are using dropwizard then you can add the following:
```xml
<dependency>
    <groupId>com.uber.jaeger</groupId>
    <artifactId>jaeger-dropwizard</artifactId>
    <version>$jaegerVersion</version>
</dependency>
```

#### JAX-RS ####

If you just want general JAX-RS 2.0 instrumentation for a framework such as Jersey then add the
following:
```xml
<dependency>
    <groupId>com.uber.jaeger</groupId>
    <artifactId>jaeger-jaxrs2</artifactId>
    <version>$jaegerVersion</version>
</dependency>
```

#### Apache Http Client ####
```xml
<dependency>
    <groupId>com.uber.jaeger</groupId>
    <artifactId>jaeger-apachehttpclient</artifactId>
    <version>$jaegerVersion</version>
</dependency>
```

#### Core ####

If you only want to do custom instrumentation using the core tracing functionality then add the
following:
```xml
<dependency>
    <groupId>com.uber.jaeger</groupId>
    <artifactId>jaeger-core</artifactId>
    <version>$jaegerVersion</version>
</dependency>
```



## JAX-RS Instrumentation ##

This project includes instrumentation for everything that implements the JAX-RS 2.0
standard. Implementations such as Jersey and Dropwizard allow you to specify client
and server filters to do some computational work before every request or response. These filters
can then be combined into a JAX-RS [Feature](https://jersey.java.net/apidocs/2.9/jersey/javax/ws/rs/core/Feature.html)

You will need to add a `JaegerFeature` to both the server and all JAX-RS clients that you use.
If you use other clients, like an Apache Http Client, make sure to instrument them as well.

### Dropwizard ###

Jaeger’s dropwizard jar exposes a configuration object `com.uber.jaeger.dropwizard.Configuration`.
If your service reads in its configuration through a pojo object then you can use this Configuration
class to read the yaml format specified above.

The following is an example of adding the Jaeger Feature to a JAX-RS based framework that creates 
a dropwizard/jersey client.

Client instrumentation example:
```java
import com.uber.jaeger.context.TracingUtils;

// JaegerConfig gets initialized by whichever dropwizard configuration reader you are using.
com.uber.jaeger.dropwizard.Configuration jaegerConfig;

// For context propagation across threads
ExecutorService tracedExecutor = TracingUtils.tracedExecutor(Executors.newCachedThreadPool());

// Enable tracing for inbound requests
JaegerFeature jaegerFeature = new JaegerFeature(configuration.getJaegerConfig());


RxClient<RxListenableFutureInvoker> reactiveClient = 
    Rx.from(
        ClientBuilder.newClient().register(jaegerFeature),
        RxListenableFutureInvoker.class,
        tracedExecutor);

Client inertClient = ClientBuilder.newClient()
    /* Arbitrary configuration code … */
    .register(jaegerFeature);
```

Server instrumentation example:
```java
@Override
public void run(T configuration, Environment environment) throws Exception {
    JaegerFeature jaegerFeature = new JaegerFeature(configuration.getJaegerConfig());
    environment.jersey().register(jaegerFeature);
}
```

#### Configuration ####

The minimal configuration in base.yaml should look like this:
```yaml
jaeger:
  serviceName: geocache
```
In a yaml configuration file you must specify a `serviceName`. It is also possible to specify
a ‘disable’ flag set to true, or false.  Jaeger should ALWAYS be ENABLED except in case of
emergencies where you are sure there is a problem with Jaeger.  If the `disable` field is left out
then Jaeger will be enabled by default.

#### Metrics ####
In order for Jaeger to report metrics properly it needs to be configured with a metric registry.  
```java
import com.uber.jaeger.dropwizard.TracingUtils;

// Dropwizard's metrics registry class
MetricsRegistry registry = new MetricsRegistry();

// JaegerConfig gets initialized by whichever dropwizard configuration reader you are using.
com.uber.jaeger.dropwizard.Configuration jaegerConfig;

jaegerConfig.setMetricRegistry(registry);

final Client client = new JerseyClientBuilder(env)
    /* Arbitrary configuration code … */
    .register(TracingUtils.clientFilter(jaegerConfig);
```

## Apache http client instrumentation ##
See Apache http client's [README.md](./jaeger-apachehttpclient/README.md)

## Thread Pooling: ##
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

## Testing ##

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

Tags.SAMPLING_PRIORITY.set(span, (short) 1);
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

 1. `git submodule init update`
 2. `./gradlew googleJavaFormat clean test`
 
### Code Style

This project uses [google java style](https://google.github.io/styleguide/javaguide.html).
It is recommended to set up a git precommit hook as follows.
```
cat>.git/hooks/pre-commit
#!/bin/sh
#
# Pre-commit hooks
# Format code using the google formatter
echo "pre-commit code format"
./gradlew googleJavaFormat
^D
```
```
chmod a+x .git/hooks/pre-commit
```

You can also setup the [IntelliJ plugin](https://plugins.jetbrains.com/plugin/8527)
to reformat code from within your IDE

## License
  
  [The MIT License](LICENSE).



  [ci-img]: https://travis-ci.org/uber/jaeger-client-java.svg?branch=master
  [ci]: https://travis-ci.org/uber/jaeger-client-java
  [cov-img]: https://coveralls.io/repos/github/uber/jaeger-client-java/badge.svg?branch=master
  [cov]: https://coveralls.io/github/uber/jaeger-client-java?branch=master
  [maven-img]: https://img.shields.io/maven-central/v/com.uber.jaeger/jaeger-core.svg?maxAge=2000
  [maven]: http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.uber.jaeger%22
