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

import java.util.HashMap;
import java.util.Random;

import com.uber.jaeger.metrics.InMemoryStatsReporter;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.utils.Utils;
import io.opentracing.tag.Tags;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Utils.class)
public class SpanTest {
    private Tracer tracer;
    private Span span;
    private InMemoryStatsReporter metricsReporter;
    private SpanContext context;

    @Before
    public void setUp() throws Exception {
        metricsReporter = new InMemoryStatsReporter();

        tracer = new Tracer.Builder("SamplerTest", new InMemoryReporter(), new ConstSampler(true))
                .withStatsReporter(metricsReporter)
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
    public void testSpanFinish() {
        Span span = (Span) tracer.buildSpan("test-service-name").withStartTimestamp(333).start();

        PowerMockito.mockStatic(Utils.class);
        BDDMockito.given(Utils.getMicroseconds()).willReturn(999L);

        span.finish();
        InMemoryReporter reporter = (InMemoryReporter) tracer.getReporter();
        assertEquals(reporter.getSpans().size(), 1);
        assertEquals(span.getDuration(), 666);
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
        Object expectedPayload = this.tracer;

        span.log(expectedTimestamp, expectedLog, expectedPayload);

        LogData actualLogData = span.getLogs().get(0);

        assertEquals(expectedTimestamp, actualLogData.getTime());
        assertEquals(expectedLog, actualLogData.getMessage());
        assertEquals(expectedPayload, actualLogData.getPayload());
    }

    @Test
    public void testLogWithTimestamp() {
        long expectedTimestamp = 2222;
        String expectedLog = "some-log";
        Object expectedPayload = this.tracer;

        PowerMockito.mockStatic(Utils.class);
        BDDMockito.given(Utils.getMicroseconds()).willReturn(expectedTimestamp);

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
