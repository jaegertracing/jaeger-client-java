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
package com.uber.jaeger.senders;

import java.util.ArrayList;
import java.util.List;

import com.uber.jaeger.Span;
import com.uber.jaeger.SpanContext;
import com.uber.jaeger.Tracer;
import com.uber.jaeger.agent.thrift.Agent;
import com.uber.jaeger.exceptions.SenderException;
import com.uber.jaeger.metrics.InMemoryStatsReporter;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.reporters.Reporter;
import com.uber.jaeger.reporters.protocols.TestTServer;
import com.uber.jaeger.reporters.protocols.ThriftSpanConverter;
import com.uber.jaeger.samplers.ConstSampler;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.transport.AutoExpandingBufferWriteTransport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UDPSenderTest {
    final String destHost = "localhost";
    int destPort;
    int localPort = 0;
    final int maxPacketSize = 1000;

    Tracer tracer;
    Reporter reporter;
    UDPSender sender;
    TestTServer server;
    ThriftSpanConverter converter;

    private TestTServer startServer() throws Exception {
        TestTServer server = new TestTServer(localPort);
        destPort = server.getPort();

        Thread t = new Thread(server);
        t.start();

        Thread.sleep(5);
        return server;
    }

    @Before
    public void setUp() throws Exception {
        server = startServer();
        reporter = new InMemoryReporter();
        tracer = new Tracer.Builder("test-sender", reporter, new ConstSampler(true))
                .withStatsReporter(new InMemoryStatsReporter())
                .build();
        sender = new UDPSender(destHost, destPort, maxPacketSize);
        converter = new ThriftSpanConverter();
    }

    @After
    public void tearDown() throws Exception {
        server.close();
        sender.close();
        reporter.close();
    }

    @Test(expected=SenderException.class)
    public void testAppendSpanTooLarge() throws Exception {
        Span jaegerSpan = (Span) tracer.buildSpan("raza").start();
        String msg = "";
        for (int i = 0 ; i < 1001; i++) {
            msg += ".";
        }

        jaegerSpan.log(msg, new Object());
        com.twitter.zipkin.thriftjava.Span span = ThriftSpanConverter.convertSpan(jaegerSpan);
        try {
            sender.append(span);
        } catch (SenderException e) {
            assertEquals(e.getDroppedSpanCount(), 1);
            throw e;
        }
    }

    @Test
    public void testAppend() throws Exception {
        // find size of the initial span
        AutoExpandingBufferWriteTransport memoryTransport =  new AutoExpandingBufferWriteTransport(maxPacketSize, 2);
        com.twitter.zipkin.thriftjava.Span span = ThriftSpanConverter.convertSpan((Span) tracer.buildSpan("raza").start());
        span.write(new TCompactProtocol(memoryTransport));
        int spanSize = memoryTransport.getPos();

        // create a sender thats a multiple of the span size (accounting for span overhead)
        // this allows us to test the boundary conditions of writing spans.
        int expectedNumSpans = 11;
        int maxPacketSize = (spanSize * expectedNumSpans) + sender.emitZipkinBatchOverhead;
        sender = new UDPSender(destHost, destPort, maxPacketSize);

        int maxPacketSizeLeft = maxPacketSize - sender.emitZipkinBatchOverhead;
        // add enough spans to be under buffer limit
        while (spanSize < maxPacketSizeLeft) {
            sender.append(span);
            maxPacketSizeLeft -= spanSize;
        }

        // add a span that overflows the limit to hit the last branch
        int result = sender.append(span);

        assertEquals(expectedNumSpans, result);
    }

    @Test
    public void testFlushSendsSpan() throws Exception {
        int timeout = 50; // in milliseconds
        int expectedNumSpans = 1;
        Span expectedSpan = (Span)tracer.buildSpan("raza").start();
        com.twitter.zipkin.thriftjava.Span span = ThriftSpanConverter.convertSpan(expectedSpan);
        int appendNum = sender.append(span);
        int flushNum = sender.flush();
        assertEquals(appendNum, 0);
        assertEquals(flushNum, 1);

        List<com.twitter.zipkin.thriftjava.Span> spans = server.getSpans(expectedNumSpans, timeout);
        assertEquals(spans.size(), expectedNumSpans);

        com.twitter.zipkin.thriftjava.Span actualSpan = spans.get(0);
        SpanContext context = expectedSpan.getContext();

        assertEquals(context.getTraceID(), actualSpan.getTrace_id());
        assertEquals(context.getSpanID(), actualSpan.getId());
        assertEquals(context.getParentID(), actualSpan.getParent_id());
        assertEquals(expectedSpan.getOperationName(), actualSpan.getName());
    }

    @Test
    public void testEmitZipkinBatchOverhead() throws Exception {
        int a = calculateZipkinBatchOverheadDifference(1);
        int b = calculateZipkinBatchOverheadDifference(2);

        // This value has been empirically observed to be 22.
        // If this test breaks it means we have changed our protocol, or
        // the protocol information has changed (likely due to a new version of thrift).
        assertEquals(a, b);
        assertEquals(b, UDPSender.emitZipkinBatchOverhead);
    }

    private int calculateZipkinBatchOverheadDifference(int numberOfSpans) throws Exception {
        AutoExpandingBufferWriteTransport memoryTransport = new AutoExpandingBufferWriteTransport(maxPacketSize, 2);
        Agent.Client memoryClient = new Agent.Client(new TCompactProtocol(memoryTransport));
        Span jaegerSpan = (Span)tracer.buildSpan("raza").start();
        com.twitter.zipkin.thriftjava.Span span = ThriftSpanConverter.convertSpan(jaegerSpan);
        List<com.twitter.zipkin.thriftjava.Span> spans = new ArrayList<>();
        for (int i = 0; i < numberOfSpans; i++) {
            spans.add(span);
        }

        memoryClient.emitZipkinBatch(spans);
        int emitZipkinBatchOverheadMultipleSpans = memoryTransport.getPos();

        memoryTransport.reset();
        for (int j = 0; j < numberOfSpans; j++) {
            span.write(new TCompactProtocol(memoryTransport));
        }
        int writeZipkinBatchOverheadMultipleSpans = memoryTransport.getPos();

        return emitZipkinBatchOverheadMultipleSpans - writeZipkinBatchOverheadMultipleSpans;
    }
}
