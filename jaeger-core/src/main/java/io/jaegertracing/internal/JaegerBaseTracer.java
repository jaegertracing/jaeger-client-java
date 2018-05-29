/*
 * Copyright (c) 2016, Uber Technologies, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.jaegertracing.internal;

import io.jaegertracing.JaegerTracer;
import io.jaegertracing.SamplingStatus;
import io.jaegertracing.internal.baggage.BaggageSetter;
import io.jaegertracing.internal.exceptions.EmptyIpException;
import io.jaegertracing.internal.exceptions.NotFourOctetsException;
import io.jaegertracing.internal.exceptions.UnsupportedFormatException;
import io.jaegertracing.internal.metrics.Metrics;
import io.jaegertracing.internal.utils.Clock;
import io.jaegertracing.internal.utils.Utils;
import io.jaegertracing.spi.BaggageRestrictionManager;
import io.jaegertracing.spi.Extractor;
import io.jaegertracing.spi.Injector;
import io.jaegertracing.spi.Reporter;
import io.jaegertracing.spi.Sampler;
import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import java.io.Closeable;
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

@ToString(exclude = {"registry", "clock", "metrics", "scopeManager"})
@Slf4j
public class JaegerBaseTracer implements JaegerTracer, Closeable {

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
  private final ScopeManager scopeManager;
  private final BaggageSetter baggageSetter;
  private final boolean expandExceptionLogs;

  public JaegerBaseTracer(
      String serviceName,
      Reporter reporter,
      Sampler sampler,
      PropagationRegistry registry,
      Clock clock,
      Metrics metrics,
      Map<String, Object> tags,
      boolean zipkinSharedRpcSpan,
      ScopeManager scopeManager,
      BaggageRestrictionManager baggageRestrictionManager,
      boolean expandExceptionLogs) {
    this.serviceName = serviceName;
    this.reporter = reporter;
    this.sampler = sampler;
    this.registry = registry;
    this.clock = clock;
    this.metrics = metrics;
    this.zipkinSharedRpcSpan = zipkinSharedRpcSpan;
    this.scopeManager = scopeManager;
    this.baggageSetter = new BaggageSetter(baggageRestrictionManager, metrics);
    this.expandExceptionLogs = expandExceptionLogs;

    this.version = loadVersion();

    tags.put(Constants.JAEGER_CLIENT_VERSION_TAG_KEY, this.version);
    if (tags.get(Constants.TRACER_HOSTNAME_TAG_KEY) == null) {
      String hostname = getHostName();
      if (hostname != null) {
        tags.put(Constants.TRACER_HOSTNAME_TAG_KEY, hostname);
      }
    }
    int ipv4;
    Object ipTag = tags.get(Constants.TRACER_IP_TAG_KEY);
    if (ipTag == null) {
      try {
        tags.put(Constants.TRACER_IP_TAG_KEY, InetAddress.getLocalHost().getHostAddress());
        ipv4 = Utils.ipToInt(Inet4Address.getLocalHost().getHostAddress());
      } catch (UnknownHostException e) {
        ipv4 = 0;
      }
    } else {
      try {
        ipv4 = Utils.ipToInt(ipTag.toString());
      } catch (EmptyIpException e) {
        ipv4 = 0;
      } catch (NotFourOctetsException e) {
        ipv4 = 0;
      }
    }
    this.ipv4 = ipv4;
    this.tags = Collections.unmodifiableMap(tags);
  }

  @Override
  public String getVersion() {
    return version;
  }

  public Metrics getMetrics() {
    return metrics;
  }

  @Override
  public String getServiceName() {
    return serviceName;
  }

  @Override
  public Map<String, ?> tags() {
    return tags;
  }

  @Override
  public int getIpv4() {
    return ipv4;
  }

  Clock clock() {
    return clock;
  }

  @Override
  public Reporter getReporter() {
    return reporter;
  }

  void reportSpan(Span span) {
    reporter.report(span);
    metrics.spansFinished.inc(1);
  }

  @Override
  public ScopeManager scopeManager() {
    return scopeManager;
  }

  @Override
  public io.opentracing.Span activeSpan() {
    Scope scope = this.scopeManager.active();
    return scope == null ? null : scope.span();
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
  @Override
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
    public io.opentracing.Tracer.SpanBuilder asChildOf(io.opentracing.Span parent) {
      return addReference(References.CHILD_OF, parent != null ? parent.context() : null);
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
        flags = (byte) (flags | SpanContext.flagSampled | SpanContext.flagDebug);
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
    public io.opentracing.Span start() {
      SpanContext context;

      // Check if active span should be established as CHILD_OF relationship
      if (references.isEmpty() && !ignoreActiveSpan && null != scopeManager.active()) {
        asChildOf(scopeManager.active().span());
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
              JaegerBaseTracer.this,
              operationName,
              context,
              startTimeMicroseconds,
              startTimeNanoTicks,
              computeDurationViaNanoTicks,
              tags,
              references);
      if (context.isSampled()) {
        metrics.spansStartedSampled.inc(1);
      } else {
        metrics.spansStartedNotSampled.inc(1);
      }
      return span;
    }

    @Override
    public Scope startActive(boolean finishSpanOnClose) {
      return scopeManager.activate(start(), finishSpanOnClose);
    }

    @Override
    public io.opentracing.Tracer.SpanBuilder ignoreActiveSpan() {
      ignoreActiveSpan = true;
      return this;
    }

    @Override
    @Deprecated
    public io.opentracing.Span startManual() {
      return start();
    }
  }

  private static String loadVersion() {
    String version;
    try {
      InputStream is = JaegerTracer.class.getResourceAsStream("jaeger.properties");
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

  private String getHostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      log.error("Cannot obtain host name", e);
      return null;
    }
  }

  @Override
  public SpanContext setBaggage(Span span, String key, String value) {
    return baggageSetter.setBaggage(span, key, value);
  }

  boolean isExpandExceptionLogs() {
    return this.expandExceptionLogs;
  }
}
