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
package com.uber.jaeger.metrics;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

public class Metrics {
    public Metrics(StatsFactory factory) {
        for (Field field : Metrics.class.getDeclaredFields()) {
            if (!Counter.class.isAssignableFrom(field.getType()) &&
                !Timer.class.isAssignableFrom(field.getType())   &&
                !Gauge.class.isAssignableFrom(field.getType())) {
                // Some frameworks dynamically add code that this reflection will pick up
                // I only want this classes Stats based fields to be picked up.
                continue;
            }

            String metricName = "jaeger.";
            HashMap<String, String> tags = new HashMap<>();

            Annotation[] annotations = field.getAnnotations();
            for (Annotation anno : annotations) {
                if (anno.annotationType().equals(Metric.class)) {
                    Metric metricAnno = (Metric) anno;
                    metricName += metricAnno.name();

                    Tag[] entries = metricAnno.tags();
                    for (Tag t : entries) {
                        tags.put(t.key(), t.value());
                    }
                }
            }

            try {
                if (field.getType().equals(Counter.class)) {
                    field.set(this, factory.createCounter(metricName, tags));
                } else if (field.getType().equals(Gauge.class)) {
                    field.set(this, factory.createGauge(metricName, tags));
                } else if (field.getType().equals(Timer.class)) {
                    field.set(this, factory.createTimer(metricName, tags));
                } else {
                    throw new RuntimeException("A field type that was neither Counter, Gauge, or Timer was parsed in reflection.");
                }
            } catch (ReflectiveOperationException e) {
                // This exception should only happen at the start of a program if the code below is not set up correctly.
                // As long as tests are run this code should never be thrown in production.
                throw new RuntimeException("No reflection exceptions should be thrown unless there is a fundamental error in your code set up.", e);
            }
        }
    }

    public static String addTagsToMetricName(String name, Map<String, String> tags) {
        if (tags == null || tags.size() == 0) {
            return name;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(name);

        SortedSet<String> sortedKeys = new TreeSet<>(tags.keySet());
        for (String key : sortedKeys) {
            sb.append(".");
            sb.append(key);
            sb.append("=");
            sb.append(tags.get(key));
        }
        return sb.toString();
    }

    public static Metrics fromStatsReporter(StatsReporter reporter) {
        return new Metrics(new StatsFactoryImpl(reporter));
    }

    @Metric(name = "traces", tags = {
            @Tag(key = "state", value = "started"),
            @Tag(key = "sampled", value = "y")
    })
    // Number of traces started by this tracer as sampled
    public Counter traceStartedSampled;

    @Metric(name = "traces", tags = {
            @Tag(key = "state", value = "started"),
            @Tag(key = "sampled", value = "n")
    })
    // Number of traces started by this tracer as not sampled
    public Counter traceStartedNotSampled;

    @Metric(name = "traces", tags = {
            @Tag(key = "state", value = "joined"),
            @Tag(key = "sampled", value = "y")
    })
    // Number of externally started sampled traces this tracer joined
    public Counter tracesJoinedSampled;


    @Metric(name = "traces", tags = {
            @Tag(key = "state", value = "joined"),
            @Tag(key = "sampled", value = "n")
    })
    // Number of externally started not-sampled traces this tracer joined
    public Counter tracesJoinedNotSampled;

    @Metric(name = "spans", tags = {
            @Tag(key = "state", value = "started"),
            @Tag(key = "group", value = "lifecycle")
    })
    // Number of sampled spans started by this tracer
    public Counter spansStarted;

    @Metric(name = "spans", tags = {
            @Tag(key = "state", value = "finished"),
            @Tag(key = "group", value = "lifecycle")
    })
    // Number of sampled spans started by this tracer
    public Counter spansFinished;

    @Metric(name = "spans", tags = {
            @Tag(key = "group", value = "sampling"),
            @Tag(key = "sampled", value = "y")
    })
    // Number of sampled spans started by this tracer
    public Counter spansSampled;

    @Metric(name = "spans", tags = {
            @Tag(key = "group", value = "sampling"),
            @Tag(key = "sampled", value = "n")
    })
    // Number of not-sampled spans started by this tracer
    public Counter spansNotSampled;

    @Metric(name = "decoding-errors")
    // Number of errors decoding tracing context
    public Counter decodingErrors;

    @Metric(name = "reporter-spans", tags = {
            @Tag(key = "state", value = "success")
    })
    // Number of spans successfully reported
    public Counter reporterSuccess;

    @Metric(name = "reporter-spans", tags = {
            @Tag(key = "state", value = "failure")
    })
    // Number of spans in failed attempts to report
    public Counter reporterFailure;

    @Metric(name = "spans", tags = {
            @Tag(key = "state", value = "dropped")
    })
    // Number of spans dropped due to internal queue overflow
    public Counter reporterDropped;

    @Metric(name = "reporter-queue")
    // Current number of spans in the reporter queue
    public Gauge reporterQueueLength;

    @Metric(name = "sampler", tags = {
            @Tag(key = "state", value = "retrieved")
    })
    // Number of times the Sampler succeeded to retrieve samping strategy
    public Counter samplerRetrieved;

    @Metric(name = "sampler", tags = {
            @Tag(key = "state", value = "updated"),
    })
    // Number of times the Sampler succeeded to retrieve and updateGauge sampling strategy
    public Counter samplerUpdated;

    @Metric(name = "sampler", tags = {
            @Tag(key = "state", value = "failure"),
            @Tag(key = "phase", value = "query")
    })
    // Number of times the Sampler failed to retrieve the sampling strategy
    public Counter samplerQueryFailure;

    @Metric(name = "sampler", tags = {
            @Tag(key = "state", value = "failure"),
            @Tag(key = "phase", value = "parsing")
    })
    // Number of times the Sampler failed to parse retrieved sampling strategy
    public Counter samplerParsingFailure;

    @Metric(name="spans", tags = {
            @Tag(key = "instrumented", value = "client"),
            @Tag(key = "type", value = "request")
    })
    // Number of exceptions being emitted from a client filter request
    public Counter clientFilterExceptionRequest;

    @Metric(name="spans", tags = {
            @Tag(key = "instrumented", value = "client"),
            @Tag(key = "type", value = "response")
    })
    // Number of exceptions being emitted from a client filter response
    public Counter clientFilterExceptionResponse;

    @Metric(name="spans", tags = {
            @Tag(key = "instrumented", value = "server"),
            @Tag(key = "type", value = "request"),
    })
    // Number of exceptions being emitted from a server filter request
    public Counter serverFilterExceptionRequest;

    @Metric(name="spans", tags = {
            @Tag(key = "instrumented", value = "server"),
            @Tag(key = "type", value = "response"),
    })
    // Number of exceptions being emitted from a server filter response
    public Counter serverFilterExceptionResponse;
}
