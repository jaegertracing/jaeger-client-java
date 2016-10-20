/*
 * Copyright (c) 2016, Uber Technologies, Inc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.uber.jaeger;

import com.uber.jaeger.exceptions.UnsupportedFormatException;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.metrics.StatsReporter;
import com.uber.jaeger.propagation.Extractor;
import com.uber.jaeger.propagation.Injector;
import com.uber.jaeger.propagation.TextMapCodec;
import com.uber.jaeger.reporters.Reporter;
import com.uber.jaeger.samplers.Sampler;
import com.uber.jaeger.utils.Clock;
import com.uber.jaeger.utils.SystemClock;
import com.uber.jaeger.utils.Utils;
import io.opentracing.References;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class Tracer implements io.opentracing.Tracer {
  public static final String VERSION = loadVersion();
  private final static Logger logger = LoggerFactory.getLogger(Tracer.class);

  private final String serviceName;
  private final Reporter reporter;
  private final Sampler sampler;
  private final PropagationRegistry registry;
  private final Clock clock;
  private final Metrics metrics;
  private final int ip;
  private final Map<String, Object> tags;

  private Tracer(
      String serviceName,
      Reporter reporter,
      Sampler sampler,
      PropagationRegistry registry,
      Clock clock,
      Metrics metrics) {
    this.serviceName = serviceName;
    this.reporter = reporter;
    this.sampler = sampler;
    this.registry = registry;
    this.clock = clock;
    this.metrics = metrics;

    int ip;
    try {
      ip = Utils.ipToInt(Inet4Address.getLocalHost().getHostAddress());
    } catch (UnknownHostException e) {
      ip = 0;
    }
    this.ip = ip;

    Map<String, Object> tags = new HashMap<>();
    tags.put("jaeger.version", Tracer.VERSION);
    String hostname = getHostName();
    if (hostname != null) {
      tags.put("jaeger.hostname", hostname);
    }
    this.tags = Collections.unmodifiableMap(tags);
  }

  public Metrics getMetrics() {
    return metrics;
  }

  public String getServiceName() {
    return serviceName;
  }

  public int getIP() {
    return ip;
  }

  Clock clock() {
    return clock;
  }

  Reporter getReporter() {
    return reporter;
  }

  void reportSpan(Span span) {
    reporter.report(span);
    metrics.spansFinished.inc(1);
  }

  @Override
  public io.opentracing.Tracer.SpanBuilder buildSpan(String operationName) {
    return new SpanBuilder(operationName);
  }

  @Override
  public <T> void inject(io.opentracing.SpanContext spanContext, Format<T> format, T carrier) {
    Injector<T> injector = registry.getInjector(format);
    if (injector == null) {
      throw new UnsupportedFormatException(format);
    }
    injector.inject((SpanContext) spanContext, carrier);
  }

  @Override
  public <T> io.opentracing.SpanContext extract(Format<T> format, T carrier) {
    Extractor<T> extractor = registry.getExtractor(format);
    if (extractor == null) {
      throw new UnsupportedFormatException(format);
    }
    return extractor.extract(carrier);
  }

  /**
   * Shuts down the {@link Reporter} and {@link Sampler}
   */
  public void close(){
    reporter.close();
    sampler.close();
  }

  private class SpanBuilder implements io.opentracing.Tracer.SpanBuilder {

    private String operationName = null;
    private long startTimeMicroseconds;
    private SpanContext parent;
    private final Map<String, Object> tags = new HashMap<>();

    SpanBuilder(String operationName) {
      this.operationName = operationName;
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
      if (parent == null) {
        return Collections.emptyList();
      }
      return parent.baggageItems();
    }

    @Override
    public io.opentracing.Tracer.SpanBuilder asChildOf(io.opentracing.SpanContext parent) {
      return addReference(References.CHILD_OF, parent);
    }

    @Override
    public io.opentracing.Tracer.SpanBuilder asChildOf(io.opentracing.Span parent) {
      return addReference(References.CHILD_OF, parent.context());
    }

    @Override
    public io.opentracing.Tracer.SpanBuilder addReference(
        String referenceType, io.opentracing.SpanContext referencedContext) {
      if (parent == null
          && (Objects.equals(referenceType, References.CHILD_OF)
              || Objects.equals(referenceType, References.FOLLOWS_FROM))) {
        this.parent = (SpanContext) referencedContext;
      }
      return this;
    }

    @Override
    public io.opentracing.Tracer.SpanBuilder withTag(String key, String value) {
      tags.put(key, value);
      return this;
    }

    @Override
    public io.opentracing.Tracer.SpanBuilder withTag(String key, boolean value) {
      tags.put(key, value);
      return this;
    }

    @Override
    public io.opentracing.Tracer.SpanBuilder withTag(String key, Number value) {
      tags.put(key, value);
      return this;
    }

    @Override
    public io.opentracing.Tracer.SpanBuilder withStartTimestamp(long microseconds) {
      this.startTimeMicroseconds = microseconds;
      return this;
    }

    private SpanContext createNewContext(String debugID) {
      long id = Utils.uniqueID();

      byte flags = 0;
      if (debugID != null) {
        flags |= SpanContext.flagSampled | SpanContext.flagDebug;
        tags.put(Constants.DEBUG_ID_HEADER_KEY, debugID);
        metrics.traceStartedSampled.inc(1);
      } else if (sampler.isSampled(id)) {
        flags |= SpanContext.flagSampled;
        tags.putAll(sampler.getTags());
        metrics.traceStartedSampled.inc(1);
      } else {
        metrics.traceStartedNotSampled.inc(1);
      }
      return new SpanContext(id, id, 0, flags);
    }

    private SpanContext createChildContext() {
      // For server-side RPC spans we reuse spanID per Zipkin convention
      if (isRPCServer()) {
        if (parent.isSampled()) {
          metrics.tracesJoinedSampled.inc(1);
        } else {
          metrics.tracesJoinedNotSampled.inc(1);
        }
        return parent;
      }
      return new SpanContext(
          parent.getTraceID(),
          Utils.uniqueID(),
          parent.getSpanID(),
          parent.getFlags(),
          parent.baggage(),
          null);
    }

    private boolean isRPCServer() {
      return tags.get(Tags.SPAN_KIND.getKey()) == Tags.SPAN_KIND_SERVER;
    }

    @Override
    public io.opentracing.Span start() {
      SpanContext context;
      if (parent == null) {
        context = createNewContext(null);
      } else if (parent.isDebugIDContainerOnly()) {
        context = createNewContext(parent.getDebugID());
      } else {
        context = createChildContext();
      }

      long startTimeNanoTicks = 0;
      boolean computeDurationViaNanoTicks = false;

      if (startTimeMicroseconds == 0) {
        startTimeMicroseconds = clock.currentTimeMicros();
        if (!clock.isMicrosAccurate()) {
          startTimeNanoTicks = clock.currentNanoTicks();
          computeDurationViaNanoTicks = true;
        }
      }

      if (parent == null || isRPCServer()) {
        // add tracer tags only to first span in the process
        tags.putAll(Tracer.this.tags);
      }

      Span span =
          new Span(
              Tracer.this,
              operationName,
              context,
              startTimeMicroseconds,
              startTimeNanoTicks,
              computeDurationViaNanoTicks,
              tags);
      if (context.isSampled()) {
        metrics.spansSampled.inc(1);
      } else {
        metrics.spansNotSampled.inc(1);
      }
      metrics.spansStarted.inc(1);
      return span;
    }
  }

  /**
   * Builds Jaeger Tracer with options.
   */
  public static final class Builder {
    private final Sampler sampler;
    private final Reporter reporter;
    private final PropagationRegistry registry = new PropagationRegistry();
    private Metrics metrics;
    private String serviceName;
    private Clock clock = new SystemClock();

    public Builder(String serviceName, Reporter reporter, Sampler sampler) {
      if (serviceName == null || serviceName.trim().length() == 0) {
        throw new IllegalArgumentException("serviceName must not be null or empty");
      }
      this.serviceName = serviceName;
      this.reporter = reporter;
      this.sampler = sampler;
      this.metrics = new Metrics(new StatsFactoryImpl(new NullStatsReporter()));

      TextMapCodec textMapCodec = new TextMapCodec(false);
      this.registerInjector(Format.Builtin.TEXT_MAP, textMapCodec);
      this.registerExtractor(Format.Builtin.TEXT_MAP, textMapCodec);
      TextMapCodec httpCodec = new TextMapCodec(true);
      this.registerInjector(Format.Builtin.HTTP_HEADERS, httpCodec);
      this.registerExtractor(Format.Builtin.HTTP_HEADERS, httpCodec);
      // TODO binary codec not implemented
    }

    public <T> Builder registerInjector(Format<T> format, Injector<T> injector) {
      this.registry.register(format, injector);
      return this;
    }

    public <T> Builder registerExtractor(Format<T> format, Extractor<T> extractor) {
      this.registry.register(format, extractor);
      return this;
    }

    public Builder withStatsReporter(StatsReporter statsReporter) {
      this.metrics = new Metrics(new StatsFactoryImpl(statsReporter));
      return this;
    }

    public Builder withClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    Builder withMetrics(Metrics metrics) {
      this.metrics = metrics;
      return this;
    }

    public Tracer build() {
      return new Tracer(this.serviceName, reporter, sampler, registry, clock, metrics);
    }
  }

  private static class PropagationRegistry {
    private final Map<Format<?>, Injector<?>> injectors = new HashMap<>();
    private final Map<Format<?>, Extractor<?>> extractors = new HashMap<>();

    @SuppressWarnings("unchecked")
    <T> Injector<T> getInjector(Format<T> format) {
      return (Injector<T>) injectors.get(format);
    }

    @SuppressWarnings("unchecked")
    <T> Extractor<T> getExtractor(Format<T> format) {
      return (Extractor<T>) extractors.get(format);
    }

    public <T> void register(Format<T> format, Injector<T> injector) {
      injectors.put(format, injector);
    }

    public <T> void register(Format<T> format, Extractor<T> extractor) {
      extractors.put(format, extractor);
    }
  }

  private static String loadVersion() {
    String version;
    try {
      try (InputStream is = Tracer.class.getResourceAsStream("jaeger.properties")) {
        Properties prop = new Properties();
        prop.load(is);
        version = prop.getProperty("jaeger.version");
      }
    } catch (Exception e) {
      throw new RuntimeException("Cannot read jaeger.properties", e);
    }
    if (version == null) {
      throw new RuntimeException("Cannot read jaeger.version from jaeger.properties");
    }
    return "Java-" + version;
  }

  String getHostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      logger.error("Cannot obtain host name", e);
      return null;
    }
  }
}
