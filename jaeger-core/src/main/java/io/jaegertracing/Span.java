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

package io.jaegertracing;

import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Span implements io.opentracing.Span {
  private static final Logger logger = LoggerFactory.getLogger(Span.class);
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
  private boolean finished = false; // to prevent the same span from getting reported multiple times

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
    if (key == null || (value == null && context.getBaggageItem(key) == null)) {
      //Ignore attempts to add new baggage items with null values, they're not accessible anyway
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
      if (finished) {
        logger.warn("Span has already been finished; will not be reported again.");
        return;
      }
      finished = true;

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
        if (tracer.isExpandExceptionLogs()) {
          fields = addExceptionLogs(fields);
        }
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
        logs.add(new LogData(timestampMicroseconds, event));
      }
      return this;
    }
  }

  /**
   * Creates logs related to logged exception
   *
   * @param fields map containing exception logs which are not present in fields
   * @return logged fields
   */
  private static Map<String, ?> addExceptionLogs(Map<String, ?> fields) {
    Object ex = fields.get(Fields.ERROR_OBJECT);
    if (!(ex instanceof Throwable)) {
      return fields;
    }

    Map<String, Object> errorFields = new HashMap<String, Object>(fields);
    Throwable loggedException = (Throwable) ex;

    if (fields.get(Fields.ERROR_KIND) == null) {
      errorFields.put(Fields.ERROR_KIND, loggedException.getClass().getName());
    }
    if (fields.get(Fields.MESSAGE) == null) {
      String message = loggedException.getMessage();
      if (message != null) {
        errorFields.put(Fields.MESSAGE, message);
      }
    }
    if (fields.get(Fields.STACK) == null) {
      StringWriter sw = new StringWriter();
      loggedException.printStackTrace(new PrintWriter(sw));
      errorFields.put(Fields.STACK, sw.toString());
    }
    return errorFields;
  }
}
