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

package io.jaegertracing.internal.metrics;

import io.jaegertracing.spi.MetricsFactory;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class Metrics {

  public Metrics(MetricsFactory factory) {
    this(factory, "jaeger_tracer_");
  }

  public Metrics(MetricsFactory factory, String metricsPrefix) {
    createMetrics(factory, metricsPrefix);
  }

  private void createMetrics(MetricsFactory factory, String metricsPrefix) {
    for (Field field : Metrics.class.getDeclaredFields()) {
      if (!Counter.class.isAssignableFrom(field.getType())
          && !Timer.class.isAssignableFrom(field.getType())
          && !Gauge.class.isAssignableFrom(field.getType())) {
        // Some frameworks dynamically add code that this reflection will pick up
        // I only want this classes Stats based fields to be picked up.
        continue;
      }

      StringBuilder metricBuilder = new StringBuilder(metricsPrefix);
      HashMap<String, String> tags = new HashMap<String, String>();

      Annotation[] annotations = field.getAnnotations();
      for (Annotation anno : annotations) {
        if (anno.annotationType().equals(Metric.class)) {
          Metric metricAnno = (Metric) anno;
          metricBuilder.append(metricAnno.name());

          Tag[] entries = metricAnno.tags();
          for (Tag t : entries) {
            tags.put(t.key(), t.value());
          }
        }
      }

      String metricName = metricBuilder.toString();
      try {
        if (field.getType().equals(Counter.class)) {
          field.set(this, factory.createCounter(metricName, tags));
        } else if (field.getType().equals(Gauge.class)) {
          field.set(this, factory.createGauge(metricName, tags));
        } else if (field.getType().equals(Timer.class)) {
          field.set(this, factory.createTimer(metricName, tags));
        } else {
          throw new RuntimeException(
              "A field type that was neither Counter, Gauge, or Timer was parsed in reflection.");
        }
      } catch (Exception e) {
        // This exception should only happen at the start of a program if the code below is not set up correctly.
        // As long as tests are run this code should never be thrown in production.
        throw new RuntimeException(
            "No reflection exceptions should be thrown unless there is a fundamental error in your code set up.",
            e);
      }
    }
  }

  public static String addTagsToMetricName(String name, Map<String, String> tags) {
    if (tags == null || tags.size() == 0) {
      return name;
    }

    StringBuilder sb = new StringBuilder();
    sb.append(name);

    SortedMap<String, String> sortedTags = new TreeMap<String, String>(tags);
    for (Map.Entry<String, String> entry: sortedTags.entrySet()) {
      sb.append(".");
      sb.append(entry.getKey());
      sb.append("=");
      sb.append(entry.getValue());
    }
    return sb.toString();
  }

  @Metric(
        name = "traces",
        tags = {
              @Tag(key = "state", value = "started"),
              @Tag(key = "sampled", value = "y"),
        }
  )
  // Number of traces started by this tracer as sampled
  public Counter traceStartedSampled;

  @Metric(
        name = "traces",
        tags = {
              @Tag(key = "state", value = "started"),
              @Tag(key = "sampled", value = "n"),
        }
  )
  // Number of traces started by this tracer as not sampled
  public Counter traceStartedNotSampled;

  @Metric(
        name = "traces",
        tags = {
              @Tag(key = "state", value = "joined"),
              @Tag(key = "sampled", value = "y"),
        }
  )
  // Number of externally started sampled traces this tracer joined
  public Counter tracesJoinedSampled;

  @Metric(
        name = "traces",
        tags = {
              @Tag(key = "state", value = "joined"),
              @Tag(key = "sampled", value = "n"),
        }
  )
  // Number of externally started not-sampled traces this tracer joined
  public Counter tracesJoinedNotSampled;

  @Metric(name = "started_spans", tags = @Tag(key = "sampled", value = "y"))
  // Number of sampled spans started by this tracer
  public Counter spansStartedSampled;

  @Metric(name = "started_spans", tags = @Tag(key = "sampled", value = "n"))
  // Number of unsampled spans started by this tracer
  public Counter spansStartedNotSampled;

  @Metric(name = "finished_spans")
  // Number of spans finished by this tracer
  public Counter spansFinished;

  @Metric(name = "span_context_decoding_errors")
  // Number of errors decoding tracing context
  public Counter decodingErrors;

  @Metric(name = "reporter_spans", tags = @Tag(key = "result", value = "ok"))
  // Number of spans successfully reported
  public Counter reporterSuccess;

  @Metric(name = "reporter_spans", tags = @Tag(key = "result", value = "err"))
  // Number of spans not reported due to a Sender failure
  public Counter reporterFailure;

  @Metric(name = "reporter_spans", tags = @Tag(key = "result", value = "dropped"))
  // Number of spans dropped due to internal queue overflow
  public Counter reporterDropped;

  @Metric(name = "reporter_queue_length")
  // Current number of spans in the reporter queue
  public Gauge reporterQueueLength;

  @Metric(name = "sampler_queries", tags = @Tag(key = "result", value = "ok"))
  // Number of times the Sampler succeeded to retrieve sampling strategy
  public Counter samplerRetrieved;

  @Metric(name = "sampler_queries", tags = @Tag(key = "result", value = "err"))
  // Number of times the Sampler failed to retrieve sampling strategy
  public Counter samplerQueryFailure;

  @Metric(name = "sampler_updates", tags = @Tag(key = "result", value = "ok"))
  // Number of times the Sampler succeeded to retrieve and update sampling strategy
  public Counter samplerUpdated;

  @Metric(name = "sampler_updates", tags = @Tag(key = "result", value = "err"))
  // Number of times the Sampler failed to update sampling strategy
  public Counter samplerParsingFailure;

  @Metric(name = "baggage_updates", tags = @Tag(key = "result", value = "ok"))
  // Number of times baggage was successfully written or updated on spans.
  public Counter baggageUpdateSuccess;

  @Metric(name = "baggage_updates", tags = @Tag(key = "result", value = "err"))
  // Number of times baggage failed to write or update on spans
  public Counter baggageUpdateFailure;

  @Metric(name = "baggage_truncations")
  // Number of times baggage was truncated as per baggage restrictions
  public Counter baggageTruncate;

  @Metric(name = "baggage_restrictions_updates", tags = @Tag(key = "result", value = "ok"))
  // Number of times baggage restrictions were successfully updated.
  public Counter baggageRestrictionsUpdateSuccess;

  @Metric(name = "baggage_restrictions_updates", tags = @Tag(key = "result", value = "err"))
  // Number of times baggage restrictions failed to update.
  public Counter baggageRestrictionsUpdateFailure;
}
