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
package com.uber.jaeger.reporters;

import com.uber.jaeger.Span;
import com.uber.jaeger.Tracer;
import com.uber.jaeger.metrics.InMemoryStatsReporter;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.senders.InMemorySender;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class RemoteReporterTest {
    private Reporter reporter;
    private Tracer tracer;
    private InMemorySender sender;
    private final int flushInterval = 1000; // in milliseconds
    private final int maxQueueSize = 500;
    private Metrics metrics;
    InMemoryStatsReporter metricsReporter;

    @Before
    public void setUp() throws Exception {
        metricsReporter = new InMemoryStatsReporter();
        metrics = new Metrics(new StatsFactoryImpl(metricsReporter));

        sender = new InMemorySender();
        reporter = new RemoteReporter(sender, flushInterval, maxQueueSize, metrics);
        tracer = new Tracer.Builder("test-remote-reporter", reporter, new ConstSampler(true))
                .withStatsReporter(metricsReporter)
                .build();
    }

    @Test
    public void testRemoteReporterReport() throws Exception {
        Span span = (Span) tracer.buildSpan("raza").start();
        reporter.report(span);
        Thread.sleep(5);
        List<com.twitter.zipkin.thriftjava.Span> received = sender.getReceived();

        assertEquals(received.size(), 1);
    }

    @Test
    public void testRemoteReporterFlushesOnClose() throws Exception {
        int numberOfSpans = 100;
        for (int i = 0; i < numberOfSpans; i++) {
            Span span = (Span) tracer.buildSpan("raza").start();
            reporter.report(span);
        }
        reporter.close();

        assertEquals(sender.getAppended().size(), 0);
        assertEquals(sender.getFlushed().size(), numberOfSpans);

        assertEquals(100L, metricsReporter.counters.get("jaeger.spans.group=sampling.sampled=y").longValue());
        assertEquals(100L, metricsReporter.counters.get("jaeger.spans.group=lifecycle.state=started").longValue());
        assertEquals(100L, metricsReporter.counters.get("jaeger.reporter-spans.state=success").longValue());
        assertEquals(100L, metricsReporter.counters.get("jaeger.traces.sampled=y.state=started").longValue());
    }
}
