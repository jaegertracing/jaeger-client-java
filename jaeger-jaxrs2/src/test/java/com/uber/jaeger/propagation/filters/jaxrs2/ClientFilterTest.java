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
package com.uber.jaeger.propagation.filters.jaxrs2;

import com.uber.jaeger.Tracer;
import com.uber.jaeger.context.TraceContext;
import com.uber.jaeger.filters.jaxrs2.ClientFilter;
import com.uber.jaeger.metrics.InMemoryStatsReporter;
import com.uber.jaeger.metrics.StatsFactory;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class ClientFilterTest {
    private StatsFactory statsFactory;
    private InMemoryStatsReporter statsReporter;

    @Before
    public void setUp() throws Exception {
        statsReporter = new InMemoryStatsReporter();
        statsFactory = new StatsFactoryImpl(statsReporter);
    }

    @Test
    public void testClientFilterLogsMetricOnRequest() throws Exception {
        Tracer tracer = mock(Tracer.class);
        TraceContext context = mock(TraceContext.class);
        ClientRequestContext requestContext = mock(ClientRequestContext.class);
        ClientFilter filter = new ClientFilter(tracer, context, statsFactory);

        doThrow(IOException.class).when(tracer).buildSpan(any(String.class));
        filter.filter(requestContext);

        assertEquals(1L, statsReporter.counters.get("jaeger.filter.instrumented=client.type=request").longValue());
    }

    @Test
    public void testClientFilterLogsMetricOnResponse() throws Exception {
        Tracer tracer = mock(Tracer.class);
        TraceContext context = mock(TraceContext.class);
        ClientRequestContext requestContext = mock(ClientRequestContext.class);
        ClientResponseContext responseContext = mock(ClientResponseContext.class);
        ClientFilter filter = new ClientFilter(tracer, context, statsFactory);

        doThrow(IOException.class).when(requestContext).getProperty(any(String.class));
        filter.filter(requestContext, responseContext);

        assertEquals(1L, statsReporter.counters.get("jaeger.filter.instrumented=client.type=response").longValue());
    }
}
