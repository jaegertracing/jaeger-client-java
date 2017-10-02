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
  private final Map<String, Object> tags;
  private long durationMicroseconds; // span durationMicroseconds
  private String operationName;
  private final List<Reference> references;
  private SpanContext context;
  private List<LogData> logs;

  Span(
      Tracer tracer,
      String operationName,
      SpanContext context,
      long startTimeMicroseconds,
      long startTimeNanoTicks,
      boolean computeDurationViaNanoTicks,
      Map<String, Object> tags,
      List<Reference> references) {
    this.tracer = tracer;
    this.operationName = operationName;
    this.context = context;
    this.startTimeMicroseconds = startTimeMicroseconds;
    this.startTimeNanoTicks = startTimeNanoTicks;
    this.computeDurationViaNanoTicks = computeDurationViaNanoTicks;
    this.tags = new HashMap<String, Object>();
    this.references = references != null ? new ArrayList<Reference>(references) : null;

    for (Map.Entry<String, Object> tag : tags.entrySet()) {
      setTagAsObject(tag.getKey(), tag.getValue());
    }
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

  public List<Reference> getReferences() {
    if (references == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(references);
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

  public String getServiceName() {
    synchronized (this) {
      return this.getTracer().getServiceName();
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
    if (key == null || value == null) {
      return this;
    }
    synchronized (this) {
      context = tracer.setBaggage(this, key, value);
      return this;
    }
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
  public synchronized Span setTag(String key, String value) {
    return setTagAsObject(key, value);
  }

  @Override
  public synchronized Span setTag(String key, boolean value) {
    return setTagAsObject(key, value);
  }

  @Override
  public synchronized Span setTag(String key, Number value) {
    return setTagAsObject(key, value);
  }

  private Span setTagAsObject(String key, Object value) {
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
      tags.put(key, value);
    }

    return this;
  }

  @Override
  public Span log(Map<String, ?> fields) {
    return log(tracer.clock().currentTimeMicros(), fields);
  }

  @Override
  public Span log(long timestampMicroseconds, Map<String, ?> fields) {
    synchronized (this) {
      if (fields == null) {
        return this;
      }
      if (context.isSampled()) {
        if (logs == null) {
          this.logs = new ArrayList<LogData>();
        }
        logs.add(new LogData(timestampMicroseconds, fields));
      }
      return this;
    }
  }

  @Override
  public Span log(String event) {
    return log(tracer.clock().currentTimeMicros(), event);
  }

  @Override
  public Span log(long timestampMicroseconds, String event) {
    synchronized (this) {
      if (event == null) {
        return this;
      }
      if (context.isSampled()) {
        if (logs == null) {
          this.logs = new ArrayList<LogData>();
        }
        // TODO: Consider removing payload from LogData
        logs.add(new LogData(timestampMicroseconds, event, null));
      }
      return this;
    }
  }
}
