# Dropwizard #

## Maven Dependency ##
If you are using dropwizard then you can add the following:
```xml
<dependency>
    <groupId>com.uber.jaeger</groupId>
    <artifactId>jaeger-dropwizard</artifactId>
    <version>$jaegerVersion</version>
</dependency>
```

## Instrumentation ##
This project includes instrumentation compatible with the JAX-RS standard. Implementations such as 
Jersey and Dropwizard allow you to specify client and server filters to do some computational work 
before every request or response. These filters are combined into a 
JAX-RS [Feature](https://jersey.java.net/apidocs/2.9/jersey/javax/ws/rs/core/Feature.html).

You will need to add a `JaegerFeature` to both the server and all JAX-RS clients that you use.
If you use other clients, like an Apache Http Client, make sure to instrument them as well.

Jaeger’s dropwizard jar exposes a configuration object `com.uber.jaeger.dropwizard.Configuration`.
If your service reads in its configuration through a pojo object then you can use this Configuration
class to read the yaml format specified above.

The following is an example of adding the Jaeger Feature to a JAX-RS based framework that creates 
a dropwizard/jersey client.

### Client ###
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

### Server ###
```java
@Override
public void run(T configuration, Environment environment) throws Exception {
    JaegerFeature jaegerFeature = new JaegerFeature(configuration.getJaegerConfig());
    environment.jersey().register(jaegerFeature);
}
```

## Configuration ## 

The minimal configuration in base.yaml should look like this:
```yaml
jaeger:
  serviceName: my-service-name 
```
In a yaml configuration file you must specify a `serviceName`. It is also possible to specify
a ‘disable’ flag set to true, or false.  Jaeger should ALWAYS be ENABLED except in case of
emergencies where you are sure there is a problem with Jaeger.  If the `disable` field is left out
then Jaeger will be enabled by default.

### Metrics ###
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
