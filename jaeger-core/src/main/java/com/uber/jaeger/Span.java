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

import com.twitter.zipkin.thriftjava.Endpoint;
import io.opentracing.tag.Tags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Span implements io.opentracing.Span {
  private final Tracer tracer;
  private final long startTimeMicroseconds;
  private final long startTimeNanoTicks;
  private final boolean computeDurationViaNanoTicks;
  private long durationMicroseconds; // span durationMicroseconds
  private String operationName;
  private SpanContext context;
  private Endpoint peer;
  private Map<String, Object> tags;
  private List<LogData> logs;
  private String localComponent;
  private boolean isClient;
  private boolean isRPC;

  Span(
      Tracer tracer,
      String operationName,
      SpanContext context,
      long startTimeMicroseconds,
      long startTimeNanoTicks,
      boolean computeDurationViaNanoTicks,
      Map<String, Object> tags) {
    this.tracer = tracer;
    this.operationName = operationName;
    this.context = context;
    this.startTimeMicroseconds = startTimeMicroseconds;
    this.startTimeNanoTicks = startTimeNanoTicks;
    this.computeDurationViaNanoTicks = computeDurationViaNanoTicks;
    this.tags = tags;
  }

  public String getLocalComponent() {
    return localComponent;
  }

  public long getStart() {
    return startTimeMicroseconds;
  }

  public long getDuration() {
    synchronized (this) {
      return durationMicroseconds;
    }
  }

  public Tracer getTracer() {
    return tracer;
  }

  public Endpoint getPeer() {
    synchronized (this) {
      return peer;
    }
  }

  private Endpoint getOrMakePeer() {
    synchronized (this) {
      if (peer == null) {
        peer = new Endpoint(0, (short) 0, "");
      }
      return peer;
    }
  }

  public Map<String, Object> getTags() {
    synchronized (this) {
      return Collections.unmodifiableMap(tags);
    }
  }

  @Override
  public Span setOperationName(String operationName) {
    synchronized (this) {
      this.operationName = operationName;
    }
    return this;
  }

  public String getOperationName() {
    synchronized (this) {
      return operationName;
    }
  }

  public List<LogData> getLogs() {
    synchronized (this) {
      if (logs == null) {
        return null;
      }
      return Collections.unmodifiableList(logs);
    }
  }

  @Override
  public Span setBaggageItem(String key, String value) {
    synchronized (this) {
      this.context = this.context.withBaggageItem(key, value);
    }
    return this;
  }

  @Override
  public String getBaggageItem(String key) {
    synchronized (this) {
      return this.context.getBaggageItem(key);
    }
  }

  @Override
  public String toString() {
    synchronized (this) {
      return context.contextAsString() + " - " + operationName;
    }
  }

  @Override
  public SpanContext context() {
    synchronized (this) {
      // doesn't need to be a copy since all fields are final
      return context;
    }
  }

  @Override
  public void finish() {
    if (computeDurationViaNanoTicks) {
      long nanoDuration = tracer.clock().currentNanoTicks() - startTimeNanoTicks;
      finishWithDuration(nanoDuration / 1000);
    } else {
      finish(tracer.clock().currentTimeMicros());
    }
  }

  @Override
  public void finish(long finishMicros) {
    finishWithDuration(finishMicros - startTimeMicroseconds);
  }

  private void finishWithDuration(long durationMicros) {
    synchronized (this) {
      this.durationMicroseconds = durationMicros;
    }

    if (context.isSampled()) {
      tracer.reportSpan(this);
    }
  }

  @Override
  public void close() {
    finish();
  }

  @Override
  public Span setTag(String key, String value) {
    return setTagAsObject(key, value);
  }

  @Override
  public Span setTag(String key, boolean value) {
    return setTagAsObject(key, value);
  }

  @Override
  public Span setTag(String key, Number value) {
    return setTagAsObject(key, value);
  }

  private boolean handleSpecialTag(String key, Object value) {
    // TODO use a map of handlers for special tags, instead of if/then
    if (key.equals(Tags.COMPONENT.getKey()) && value instanceof String) {
      localComponent = (String) value;
      return true;
    }

    if (key.equals(Tags.PEER_HOST_IPV4.getKey()) && value instanceof Integer) {
      getOrMakePeer().setIpv4((Integer) value);
      return true;
    }

    if (key.equals(Tags.PEER_PORT.getKey()) && value instanceof Number) {
      getOrMakePeer().setPort(((Number) value).shortValue());
      return true;
    }

    if (key.equals(Tags.PEER_SERVICE.getKey()) && value instanceof String) {
      getOrMakePeer().setService_name((String) value);
      return true;
    }

    if (key.equals(Tags.SPAN_KIND.getKey()) && value instanceof String) {
      isClient = Tags.SPAN_KIND_CLIENT.equals(value);
      boolean isServer = Tags.SPAN_KIND_SERVER.equals(value);
      isRPC = isClient || isServer;
      return true;
    }

    return false;
  }

  private Span setTagAsObject(String key, Object value) {
    synchronized (this) {
      if (key.equals(Tags.SAMPLING_PRIORITY.getKey()) && (value instanceof Number)) {
        int priority = ((Number) value).intValue();
        byte newFlags;
        if (priority > 0) {
          newFlags = (byte) (context.getFlags() | SpanContext.flagSampled | SpanContext.flagDebug);
        } else {
          newFlags = (byte) (context.getFlags() & (~SpanContext.flagSampled));
        }

        context = context.withFlags(newFlags);
      }

      if (context.isSampled()) {
        if (!handleSpecialTag(key, value)) {
          if (this.tags == null) {
            this.tags = new HashMap<>();
          }

          tags.put(key, value);
        }
      }
    }

    return this;
  }

  @Override
  public Span log(String message, /* @Nullable */ Object payload) {
    return log(tracer.clock().currentTimeMicros(), message, payload);
  }

  @Override
  public Span log(long instantMicroseconds, String message, /* @Nullable */ Object payload) {
    synchronized (this) {
      if (context.isSampled()) {
        if (logs == null) {
          this.logs = new ArrayList<>();
        }

        logs.add(new LogData(instantMicroseconds, message, payload));
      }
      return this;
    }
  }

  public boolean isRPC() {
    return isRPC;
  }

  public boolean isRPCClient() {
    return isClient;
  }
}
