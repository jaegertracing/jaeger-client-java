# Micrometer integration for Jaeger internal metrics

This module brings a [Micrometer](http://micrometer.io/) integration to the internal Jaeger metrics.

## Configuring

To configure the Micrometer integration, a custom Jaeger Tracer instance has to be used:

```java
MicrometerMetricsFactory metricsReporter = new MicrometerMetricsFactory();
Configuration configuration = new Configuration("myServiceName");
Tracer tracer = configuration
    .getTracerBuilder()
    .withMetricsFactory(metricsReporter)
    .build();
```

After that, just use Micrometer's API to get the data into a concrete backend via the registry mechanism. 
For [Prometheus](https://prometheus.io/), it means using a code like the following:

```java
PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
io.micrometer.core.instrument.Metrics.addRegistry(registry);
```

Once metric data has been captured, `registry.scrape()` can be used in an endpoint accessed by Prometheus. Refer
to the [Micrometer's documentation](http://micrometer.io/docs/concepts#_registry) on the appropriate way to achieve
that.