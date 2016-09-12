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

import com.uber.jaeger.metrics.InMemoryStatsReporter;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.utils.Clock;
import io.opentracing.tag.Tags;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SpanTest {
    private Clock clock;
    private InMemoryReporter reporter;
    private Tracer tracer;
    private Span span;
    private InMemoryStatsReporter metricsReporter;
    private SpanContext context;

    @Before
    public void setUp() throws Exception {
        metricsReporter = new InMemoryStatsReporter();
        reporter = new InMemoryReporter();
        clock = mock(Clock.class);
        tracer = new Tracer.Builder("SamplerTest", reporter, new ConstSampler(true))
                .withStatsReporter(metricsReporter)
                .withClock(clock)
                .build();
        span = (Span) tracer.buildSpan("some-operation").start();

        Random rand = new Random();
        context = new SpanContext(rand.nextLong(), rand.nextLong(), rand.nextLong(), (byte)1);
    }

    @Test
    public void testSpanMetrics() {
        assertEquals(1L, metricsReporter.counters.get("jaeger.spans.group=sampling.sampled=y").longValue());
        assertEquals(1L, metricsReporter.counters.get("jaeger.spans.group=lifecycle.state=started").longValue());
        assertEquals(1L, metricsReporter.counters.get("jaeger.traces.sampled=y.state=started").longValue());
    }

    @Test
    public void testSetAndGetBaggageItem() {
        String expected = "expected";
        String key = "some.BAGGAGE";
        span.setBaggageItem(key, expected);
        assertEquals(expected, span.getBaggageItem(key));
    }

    @Test
    public void testSetBooleanTag() {
        Boolean expected = true;
        String key = "tag.key";

        span.setTag(key, expected);
        assertEquals(expected, span.getTags().get(key));
    }

    @Test
    public void testSetOperationName() {
        String expected = "modified.operation";

        assertEquals("some-operation", span.getOperationName());
        span.setOperationName(expected);
        assertEquals(expected, span.getOperationName());
    }

    @Test
    public void testSetStringTag() {
        String expected = "expected.value";
        String key = "tag.key";

        span.setTag(key, expected);
        assertEquals(expected, span.getTags().get(key));
    }

    @Test
    public void testSetNumberTag() {
        Integer expected = 5;
        String key = "tag.key";

        span.setTag(key, expected);
        assertEquals(expected, span.getTags().get(key));
    }

    @Test
    public void testWithTimestampAccurateClock() {
        testWithTimestamp(true);
    }

    @Test
    public void testWithTimestampInaccurateClock() {
        testWithTimestamp(false);
    }

    private void testWithTimestamp(boolean accurate) {
        when(clock.isMicrosAccurate()).thenReturn(accurate);
        when(clock.currentTimeMicros())
                .thenThrow(new IllegalStateException("currentTimeMicros() called"));
        when(clock.currentNanoTicks())
                .thenThrow(new IllegalStateException("currentNanoTicks() called"));

        Span span = (Span) tracer.buildSpan("test-service-name")
                .withStartTimestamp(567)
                .start();
        span.finish(999);

        assertEquals(1, reporter.getSpans().size());
        assertEquals(567, span.getStart());
        assertEquals(999-567, span.getDuration());
    }

    @Test
    public void testWithoutTimestampsAccurateClock() {
        when(clock.isMicrosAccurate()).thenReturn(true);
        when(clock.currentTimeMicros())
                .thenReturn(1L)
                .thenReturn(5L);
        when(clock.currentNanoTicks())
                .thenThrow(new IllegalStateException("currentNanoTicks() called"));

        Span span = (Span) tracer.buildSpan("test-service-name")
                .start();
        span.finish();

        assertEquals(1, reporter.getSpans().size());
        assertEquals(1, span.getStart());
        assertEquals(4, span.getDuration());
    }

    @Test
    public void testWithoutTimestampsInaccurateClock() {
        when(clock.isMicrosAccurate()).thenReturn(false);
        when(clock.currentTimeMicros())
                .thenReturn(100L)
                .thenThrow(new IllegalStateException("currentTimeMicros() called 2nd time"));
        when(clock.currentNanoTicks())
                .thenReturn(20000L)
                .thenReturn(30000L);

        Span span = (Span) tracer.buildSpan("test-service-name")
                .start();
        span.finish();

        assertEquals(1, reporter.getSpans().size());
        assertEquals(100, span.getStart());
        assertEquals(10, span.getDuration());
    }

    @Test
    public void testSpanToString() {
        Span span = (Span) tracer.buildSpan("test-operation").start();
        SpanContext expectedContext = span.getContext();
        SpanContext actualContext = SpanContext.contextFromString(span.getContext().contextAsString());

        assertEquals(expectedContext.getTraceID(), actualContext.getTraceID());
        assertEquals(expectedContext.getSpanID(), actualContext.getSpanID());
        assertEquals(expectedContext.getParentID(), actualContext.getParentID());
        assertEquals(expectedContext.getFlags(), actualContext.getFlags());
    }

    @Test
    public void testOperationName() {
        String expectedOperation = "leela";
        Span span = (Span) tracer.buildSpan(expectedOperation).start();
        assertEquals(expectedOperation, span.getOperationName());
    }

    @Test
    public void testLog() {
        long expectedTimestamp = 2222;
        String expectedLog = "some-log";
        Object expectedPayload = new Object();

        span.log(expectedTimestamp, expectedLog, expectedPayload);

        LogData actualLogData = span.getLogs().get(0);

        assertEquals(expectedTimestamp, actualLogData.getTime());
        assertEquals(expectedLog, actualLogData.getMessage());
        assertEquals(expectedPayload, actualLogData.getPayload());
    }

    @Test
    public void testLogWithTimestamp() {
        final long expectedTimestamp = 2222;
        final String expectedLog = "some-log";
        final Object expectedPayload = new Object();

        when(clock.currentTimeMicros()).thenReturn(expectedTimestamp);

        Span span = (Span) tracer.buildSpan("test-service-operation").start();
        span.log(expectedLog, expectedPayload);

        LogData actualLogData = span.getLogs().get(0);

        assertEquals(expectedTimestamp, actualLogData.getTime());
        assertEquals(expectedLog, actualLogData.getMessage());
        assertEquals(expectedPayload, actualLogData.getPayload());
    }

    @Test
    public void testSpanDetectsEndpointTags() {
        int expectedIp = (127 << 24) | 1;
        short expectedPort = 8080;
        String expectedServiceName = "some-peer-service";
        Span span = (Span) tracer.buildSpan("test-service-operation").start();
        Tags.PEER_HOST_IPV4.set(span, expectedIp);
        Tags.PEER_PORT.set(span, expectedPort);
        Tags.PEER_SERVICE.set(span, expectedServiceName);

        assertEquals(expectedIp, span.getPeer().getIpv4());
        assertEquals(expectedPort, span.getPeer().getPort());
        assertEquals(expectedServiceName, span.getPeer().getService_name());
    }

    @Test
    public void testSpanDetectsLocalComponent() {
        String expectedComponentName = "some-lc-name";
        Span span = (Span) tracer.buildSpan("test-service-operation").start();
        Tags.COMPONENT.set(span, expectedComponentName);

        assertEquals(expectedComponentName, span.getLocalComponent());
    }

    @Test
    public void testSpanDetectsIsServer() {
        Span span = (Span) tracer.buildSpan("test-service-operation").start();
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);

        assertTrue(span.isRPCClient());
    }

    @Test
    public void testSpanDetectsSamplingPriorityGreaterThanZero() {
        Span span = (Span) tracer.buildSpan("test-service-operation").start();
        Tags.SAMPLING_PRIORITY.set(span,(short) 1);

        assertEquals(span.getContext().getFlags() & SpanContext.flagSampled, SpanContext.flagSampled);
        assertEquals(span.getContext().getFlags() & SpanContext.flagDebug, SpanContext.flagDebug);
    }

    @Test
    public void testSpanDetectsSamplingPriorityLessThanZero() {
        Span span = (Span) tracer.buildSpan("test-service-operation").start();

        assertEquals(span.getContext().getFlags() & SpanContext.flagSampled, SpanContext.flagSampled);
        Tags.SAMPLING_PRIORITY.set(span,(short) -1);
        assertEquals(span.getContext().getFlags() & SpanContext.flagSampled, 0);
    }
}
