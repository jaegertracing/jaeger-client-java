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

import com.uber.jaeger.baggage.BaggageRestrictionManager;
import com.uber.jaeger.baggage.DefaultBaggageRestrictionManager;
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
import com.uber.jaeger.samplers.SamplingStatus;
import com.uber.jaeger.utils.Clock;
import com.uber.jaeger.utils.SystemClock;
import com.uber.jaeger.utils.Utils;

import io.opentracing.ActiveSpan;
import io.opentracing.ActiveSpanSource;
import io.opentracing.BaseSpan;
import io.opentracing.References;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.ThreadLocalActiveSpanSource;

import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@ToString(exclude = {"registry", "clock", "metrics", "activeSpanSource"})
@Slf4j
public class Tracer implements io.opentracing.Tracer {

  private final String version;
  private final String serviceName;
  private final Reporter reporter;
  private final Sampler sampler;
  private final PropagationRegistry registry;
  private final Clock clock;
  private final Metrics metrics;
  private final int ipv4;
  private final Map<String, ?> tags;
  private final boolean zipkinSharedRpcSpan;
  private final ActiveSpanSource activeSpanSource;
  private final BaggageRestrictionManager baggageRestrictionManager;

  private Tracer(
      String serviceName,
      Reporter reporter,
      Sampler sampler,
      PropagationRegistry registry,
      Clock clock,
      Metrics metrics,
      Map<String, Object> tags,
      boolean zipkinSharedRpcSpan,
      ActiveSpanSource activeSpanSource,
      BaggageRestrictionManager baggageRestrictionManager) {
    this.serviceName = serviceName;
    this.reporter = reporter;
    this.sampler = sampler;
    this.registry = registry;
    this.clock = clock;
    this.metrics = metrics;
    this.zipkinSharedRpcSpan = zipkinSharedRpcSpan;
    this.activeSpanSource = activeSpanSource;
    this.baggageRestrictionManager = baggageRestrictionManager;

    this.version = loadVersion();

    tags.put(Constants.JAEGER_CLIENT_VERSION_TAG_KEY, this.version);
    String hostname = getHostName();
    if (hostname != null) {
      tags.put(Constants.TRACER_HOSTNAME_TAG_KEY, hostname);
    }
    int ipv4 ;
    try {
      tags.put(Constants.TRACER_IP_TAG_KEY, InetAddress.getLocalHost().getHostAddress());
      ipv4 = Utils.ipToInt(Inet4Address.getLocalHost().getHostAddress());
    } catch (UnknownHostException e) {
      ipv4 = 0;
    }
    this.ipv4 = ipv4;
    this.tags = Collections.unmodifiableMap(tags);
  }

  public String getVersion() {
    return version;
  }

  public Metrics getMetrics() {
    return metrics;
  }

  public String getServiceName() {
    return serviceName;
  }

  public Map<String, ?> tags() {
    return tags;
  }

  public int getIpv4() {
    return ipv4;
  }

  Clock clock() {
    return clock;
  }

  Reporter getReporter() {
    return reporter;
  }

  BaggageRestrictionManager getBaggageRestrictionManager() {
    return baggageRestrictionManager;
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

  @Override
  public ActiveSpan activeSpan() {
    return activeSpanSource.activeSpan();
  }

  @Override
  public ActiveSpan makeActive(io.opentracing.Span span) {
    return activeSpanSource.makeActive(span);
  }

  /**
   * Shuts down the {@link Reporter} and {@link Sampler}
   */
  public void close() {
    reporter.close();
    sampler.close();
  }

  //Visible for testing
  class SpanBuilder implements io.opentracing.Tracer.SpanBuilder {

    private String operationName = null;
    private long startTimeMicroseconds;
    /**
     * In 99% situations there is only one parent (childOf), so we do not want to allocate
     * a collection of references.
     */
    private List<Reference> references = Collections.emptyList();
    private final Map<String, Object> tags = new HashMap<String, Object>();
    private boolean ignoreActiveSpan = false;

    SpanBuilder(String operationName) {
      this.operationName = operationName;
    }

    @Override
    public io.opentracing.Tracer.SpanBuilder asChildOf(io.opentracing.SpanContext parent) {
      return addReference(References.CHILD_OF, parent);
    }

    @Override
    public io.opentracing.Tracer.SpanBuilder asChildOf(BaseSpan<?> parent) {
      return addReference(References.CHILD_OF, parent.context());
    }

    @Override
    public io.opentracing.Tracer.SpanBuilder addReference(
        String referenceType, io.opentracing.SpanContext referencedContext) {

      if (!(referencedContext instanceof SpanContext)) {
        return this;
      }

      // Jaeger thrift currently does not support other reference types
      if (!References.CHILD_OF.equals(referenceType)
          && !References.FOLLOWS_FROM.equals(referenceType)) {
        return this;
      }

      if (references.isEmpty()) {
        // Optimization for 99% situations, when there is only one parent
        references = Collections.singletonList(new Reference((SpanContext) referencedContext, referenceType));
      } else {
        if (references.size() == 1) {
          references = new ArrayList<Reference>(references);
        }
        references.add(new Reference((SpanContext) referencedContext, referenceType));
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

    private SpanContext createNewContext(String debugId) {
      long id = Utils.uniqueId();

      byte flags = 0;
      if (debugId != null) {
        flags |= SpanContext.flagSampled | SpanContext.flagDebug;
        tags.put(Constants.DEBUG_ID_HEADER_KEY, debugId);
        metrics.traceStartedSampled.inc(1);
      } else {
        //TODO(prithvi): Don't assume operationName is set on creation
        SamplingStatus samplingStatus = sampler.sample(operationName, id);
        if (samplingStatus.isSampled()) {
          flags |= SpanContext.flagSampled;
          tags.putAll(samplingStatus.getTags());
          metrics.traceStartedSampled.inc(1);
        } else {
          metrics.traceStartedNotSampled.inc(1);
        }
      }

      return new SpanContext(id, id, 0, flags);
    }

    private Map<String, String> createChildBaggage() {
      Map<String, String> baggage = null;

      // optimization for 99% use cases, when there is only one parent
      if (references.size() == 1) {
        return references.get(0).getSpanContext().baggage();
      }

      for (Reference reference: references) {
        if (reference.getSpanContext().baggage() != null) {
          if (baggage == null) {
            baggage = new HashMap<String, String>();
          }
          baggage.putAll(reference.getSpanContext().baggage());
        }
      }

      return baggage;
    }

    private SpanContext createChildContext() {
      SpanContext preferredReference = preferredReference();

      if (isRpcServer()) {
        if (isSampled()) {
          metrics.tracesJoinedSampled.inc(1);
        } else {
          metrics.tracesJoinedNotSampled.inc(1);
        }

        // Zipkin server compatibility
        if (zipkinSharedRpcSpan) {
          return preferredReference;
        }
      }

      return new SpanContext(
          preferredReference.getTraceId(),
          Utils.uniqueId(),
          preferredReference.getSpanId(),
          // should we do OR across passed references?
          preferredReference.getFlags(),
          createChildBaggage(),
          null);
    }

    //Visible for testing
    boolean isRpcServer() {
      return Tags.SPAN_KIND_SERVER.equals(tags.get(Tags.SPAN_KIND.getKey()));
    }

    private SpanContext preferredReference() {
      Reference preferredReference = references.get(0);
      for (Reference reference: references) {
        // childOf takes precedence as a preferred parent
        if (References.CHILD_OF.equals(reference.getType())
            && !References.CHILD_OF.equals(preferredReference.getType())) {
          preferredReference = reference;
          break;
        }
      }
      return preferredReference.getSpanContext();
    }

    private boolean isSampled() {
      if (references != null) {
        for (Reference reference : references) {
          if (reference.getSpanContext().isSampled()) {
            return true;
          }
        }
      }
      return false;
    }

    private String debugId() {
      if (references.size() == 1 && references.get(0).getSpanContext().isDebugIdContainerOnly()) {
        return references.get(0).getSpanContext().getDebugId();
      }
      return null;
    }

    @Override
    public io.opentracing.Span startManual() {
      SpanContext context;

      // Check if active span should be established as CHILD_OF relationship
      if (references.isEmpty() && !ignoreActiveSpan && null != activeSpanSource.activeSpan()) {
        asChildOf(activeSpanSource.activeSpan());
      }

      String debugId = debugId();
      if (references.isEmpty()) {
        context = createNewContext(null);
      } else if (debugId != null) {
        context = createNewContext(debugId);
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

      Span span =
          new Span(
              Tracer.this,
              operationName,
              context,
              startTimeMicroseconds,
              startTimeNanoTicks,
              computeDurationViaNanoTicks,
              tags,
              references);
      if (context.isSampled()) {
        metrics.spansSampled.inc(1);
      } else {
        metrics.spansNotSampled.inc(1);
      }
      metrics.spansStarted.inc(1);
      return span;
    }

    @Override
    public ActiveSpan startActive() {
      return activeSpanSource.makeActive(startManual());
    }

    @Override
    public io.opentracing.Tracer.SpanBuilder ignoreActiveSpan() {
      ignoreActiveSpan = true;
      return this;
    }

    @Override
    @Deprecated
    public io.opentracing.Span start() {
      return startManual();
    }
  }

  /**
   * Builds Jaeger Tracer with options.
   */
  public static final class Builder {
    private final Sampler sampler;
    private final Reporter reporter;
    private final PropagationRegistry registry = new PropagationRegistry();
    private Metrics metrics = new Metrics(new StatsFactoryImpl(new NullStatsReporter()));
    private String serviceName;
    private Clock clock = new SystemClock();
    private Map<String, Object> tags = new HashMap<String, Object>();
    private boolean zipkinSharedRpcSpan;
    private ActiveSpanSource activeSpanSource = new ThreadLocalActiveSpanSource();
    private BaggageRestrictionManager baggageRestrictionManager =
        new DefaultBaggageRestrictionManager(this.metrics);

    public Builder(String serviceName, Reporter reporter, Sampler sampler) {
      if (serviceName == null || serviceName.trim().length() == 0) {
        throw new IllegalArgumentException("serviceName must not be null or empty");
      }
      this.serviceName = serviceName;
      this.reporter = reporter;
      this.sampler = sampler;

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

    public Builder withActiveSpanSource(ActiveSpanSource activeSpanSource) {
      this.activeSpanSource = activeSpanSource;
      return this;
    }

    public Builder withClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    public Builder withZipkinSharedRpcSpan() {
      zipkinSharedRpcSpan = true;
      return this;
    }

    public Builder withMetrics(Metrics metrics) {
      this.metrics = metrics;
      return this;
    }

    public Builder withTag(String key, String value) {
      tags.put(key, value);
      return this;
    }

    public Builder withTag(String key, boolean value) {
      tags.put(key, value);
      return this;
    }

    public Builder withTag(String key, Number value) {
      tags.put(key, value);
      return this;
    }

    public Builder withTags(Map<String, String> tags) {
      if (tags != null) {
        this.tags.putAll(tags);
      }
      return this;
    }

    public Builder withBaggageRestrictionManager(BaggageRestrictionManager baggageRestrictionManager) {
      this.baggageRestrictionManager = baggageRestrictionManager;
      return this;
    }

    public Tracer build() {
      return new Tracer(this.serviceName, reporter, sampler, registry, clock, metrics, tags,
          zipkinSharedRpcSpan, activeSpanSource, baggageRestrictionManager);
    }
  }

  private static class PropagationRegistry {
    private final Map<Format<?>, Injector<?>> injectors = new HashMap<Format<?>, Injector<?>>();
    private final Map<Format<?>, Extractor<?>> extractors = new HashMap<Format<?>, Extractor<?>>();

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
      InputStream is = Tracer.class.getResourceAsStream("jaeger.properties");
      try {
        Properties prop = new Properties();
        prop.load(is);
        version = prop.getProperty(Constants.JAEGER_CLIENT_VERSION_TAG_KEY);
      } finally {
        is.close();
      }
    } catch (Exception e) {
      throw new RuntimeException("Cannot read jaeger.properties", e);
    }
    if (version == null) {
      throw new RuntimeException("Cannot read " + Constants.JAEGER_CLIENT_VERSION_TAG_KEY + " from jaeger.properties");
    }
    return "Java-" + version;
  }

  String getHostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      log.error("Cannot obtain host name", e);
      return null;
    }
  }

}
