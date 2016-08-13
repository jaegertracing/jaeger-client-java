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

import com.uber.jaeger.propagation.Injector;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.container.ContainerRequestContext;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TracerTest {

    Tracer tracer;
    InMemoryStatsReporter metricsReporter;

    @Before
    public void setUp() throws Exception {
        metricsReporter = new InMemoryStatsReporter();
        tracer = new Tracer.Builder("TracerTestService", new InMemoryReporter(), new ConstSampler(true))
            .withStatsReporter(metricsReporter)
            .build();
    }

    @Test
    public void testBuildSpan() {
        String expectedOperation = "fry";
        Span span = (com.uber.jaeger.Span) tracer.buildSpan(expectedOperation).start();

        assertEquals(expectedOperation, span.getOperationName());
    }

    @Test
    public void testTracerMetrics() {
        String expectedOperation = "fry";
        tracer.buildSpan(expectedOperation).start();
        assertEquals(1L, metricsReporter.counters.get("jaeger.spans.group=sampling.sampled=y").longValue());
        assertEquals(1L, metricsReporter.counters.get("jaeger.spans.group=lifecycle.state=started").longValue());
        assertEquals(1L, metricsReporter.counters.get("jaeger.traces.sampled=y.state=started").longValue());
    }

    @Test
    public void testRegisterInjector() {
        @SuppressWarnings("unchecked") Injector<TextMap> injector = mock(Injector.class);

        Tracer tracer = new Tracer.Builder(
                "TracerTestService",
                new InMemoryReporter(),
                new ConstSampler(true))
                .withStatsReporter(metricsReporter)
                .registerInjector(Format.Builtin.TEXT_MAP, injector).build();
        Span span = (com.uber.jaeger.Span) tracer.buildSpan("leela").start();

        TextMap carrier = mock(TextMap.class);
        tracer.inject(span.context(), Format.Builtin.TEXT_MAP, carrier);

        verify(injector).inject(any(SpanContext.class), any(TextMap.class));
    }
}
