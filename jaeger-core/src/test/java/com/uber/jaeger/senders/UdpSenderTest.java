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

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.uber.jaeger.Span;
import com.uber.jaeger.Tracer;
import com.uber.jaeger.agent.thrift.Agent;
import com.uber.jaeger.exceptions.SenderException;
import com.uber.jaeger.metrics.InMemoryStatsReporter;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.reporters.Reporter;
import com.uber.jaeger.reporters.protocols.JaegerThriftSpanConverter;
import com.uber.jaeger.reporters.protocols.TestTServer;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.thriftjava.Batch;
import com.uber.jaeger.thriftjava.Process;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.transport.AutoExpandingBufferWriteTransport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UdpSenderTest {
  static final String SERVICE_NAME = "test-sender";
  final String destHost = "localhost";
  int destPort;
  int localPort = 0;
  final int maxPacketSize = 1000;

  Tracer tracer;
  Reporter reporter;
  UdpSender sender;
  TestTServer server;

  private TestTServer startServer() throws Exception {
    TestTServer server = new TestTServer(localPort);
    destPort = server.getPort();

    Thread t = new Thread(server);
    t.start();

    // wait up to 5 seconds to get this thread started
    await()
        .with()
        .pollInterval(1, TimeUnit.MILLISECONDS)
        .atMost(5, TimeUnit.SECONDS)
        .until(() -> t.isAlive());
    return server;
  }

  @Before
  public void setUp() throws Exception {
    server = startServer();
    reporter = new InMemoryReporter();
    tracer =
        new Tracer.Builder(SERVICE_NAME, reporter, new ConstSampler(true))
            .withStatsReporter(new InMemoryStatsReporter())
            .withTag("foo", "bar")
            .build();
    sender = new UdpSender(destHost, destPort, maxPacketSize);
  }

  @After
  public void tearDown() throws Exception {
    server.close();
    sender.close();
    reporter.close();
  }

  @Test(expected = SenderException.class)
  public void testAppendSpanTooLarge() throws Exception {
    Span jaegerSpan = (Span) tracer.buildSpan("raza").startManual();
    String msg = "";
    for (int i = 0; i < 10001; i++) {
      msg += ".";
      jaegerSpan.log(msg, new Object());
    }

    try {
      sender.append(jaegerSpan);
    } catch (SenderException e) {
      assertEquals(e.getDroppedSpanCount(), 1);
      throw e;
    }
  }

  @Test
  public void testAppend() throws Exception {
    // find size of the initial span
    Span jaegerSpan = (Span)tracer.buildSpan("raza").startManual();
    com.uber.jaeger.thriftjava.Span span =
            JaegerThriftSpanConverter.convertSpan(jaegerSpan);

    Process process = new Process(tracer.getServiceName())
        .setTags(JaegerThriftSpanConverter.buildTags(tracer.tags()));

    AutoExpandingBufferWriteTransport memoryTransport =
        new AutoExpandingBufferWriteTransport(maxPacketSize, 2);

    process.write(new TCompactProtocol(memoryTransport));
    int processSize = memoryTransport.getPos();
    memoryTransport.reset();
    span.write(new TCompactProtocol((memoryTransport)));
    int spanSize = memoryTransport.getPos();

    // create a sender thats a multiple of the span size (accounting for span overhead)
    // this allows us to test the boundary conditions of writing spans.
    int expectedNumSpans = 11;
    int maxPacketSize = (spanSize * expectedNumSpans) + sender.EMIT_BATCH_OVERHEAD + processSize;
    int maxPacketSizeLeft = maxPacketSize - sender.EMIT_BATCH_OVERHEAD - processSize;
    // add enough spans to be under buffer limit
    sender = new UdpSender(destHost, destPort, maxPacketSize);
    while (spanSize < maxPacketSizeLeft) {
      sender.append(jaegerSpan);
      maxPacketSizeLeft -= spanSize;
    }

    // add a span that overflows the limit to hit the last branch
    int result = sender.append(jaegerSpan);

    assertEquals(expectedNumSpans, result);
  }

  @Test
  public void testFlushSendsSpan() throws Exception {
    int timeout = 50; // in milliseconds
    int expectedNumSpans = 1;
    Span expectedSpan = (Span) tracer.buildSpan("raza").startManual();
    int appendNum = sender.append(expectedSpan);
    int flushNum = sender.flush();
    assertEquals(appendNum, 0);
    assertEquals(flushNum, 1);

    Batch batch = server.getBatch(expectedNumSpans, timeout);
    assertEquals(batch.getSpans().size(), expectedNumSpans);

    com.uber.jaeger.thriftjava.Span actualSpan = batch.getSpans().get(0);
    assertEquals(expectedSpan.context().getTraceId(), actualSpan.getTraceIdLow());
    assertEquals(0, actualSpan.getTraceIdHigh());
    assertEquals(expectedSpan.context().getSpanId(), actualSpan.getSpanId());
    assertEquals(0, actualSpan.getParentSpanId());
    assertTrue(actualSpan.references.isEmpty());
    assertEquals(expectedSpan.getOperationName(), actualSpan.getOperationName());
    assertEquals(3, batch.getProcess().getTags().size());
    assertEquals("jaeger.hostname", batch.getProcess().getTags().get(0).getKey());
    assertEquals("jaeger.version", batch.getProcess().getTags().get(1).getKey());
    assertEquals("bar", batch.getProcess().getTags().get(2).getVStr());
  }

  @Test
  public void testEmitBatchOverhead() throws Exception {
    int a = calculateBatchOverheadDifference(1);
    int b = calculateBatchOverheadDifference(2);

    // This value has been empirically observed to be 56.
    // If this test breaks it means we have changed our protocol, or
    // the protocol information has changed (likely due to a new version of thrift).
    assertEquals(a, b);
    assertEquals(b, UdpSender.EMIT_BATCH_OVERHEAD);
  }

  private int calculateBatchOverheadDifference(int numberOfSpans) throws Exception {
    AutoExpandingBufferWriteTransport memoryTransport =
        new AutoExpandingBufferWriteTransport(maxPacketSize, 2);
    Agent.Client memoryClient = new Agent.Client(new TCompactProtocol((memoryTransport)));
    Span jaegerSpan = (Span) tracer.buildSpan("raza").startManual();
    com.uber.jaeger.thriftjava.Span span = JaegerThriftSpanConverter.convertSpan(jaegerSpan);
    List<com.uber.jaeger.thriftjava.Span> spans = new ArrayList<>();
    for (int i = 0; i < numberOfSpans; i++) {
      spans.add(span);
    }

    memoryClient.emitBatch(new Batch(new Process(SERVICE_NAME), spans));
    int emitBatchOverheadMultipleSpans = memoryTransport.getPos();

    memoryTransport.reset();
    for (int j = 0; j < numberOfSpans; j++) {
      span.write(new TCompactProtocol(memoryTransport));
    }
    int writeBatchOverheadMultipleSpans = memoryTransport.getPos();

    return emitBatchOverheadMultipleSpans - writeBatchOverheadMultipleSpans;
  }
}
