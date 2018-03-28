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

package com.uber.jaeger.senders;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.uber.jaeger.Span;
import com.uber.jaeger.Tracer;
import com.uber.jaeger.exceptions.SenderException;
import com.uber.jaeger.metrics.InMemoryStatsReporter;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.reporters.Reporter;
import com.uber.jaeger.reporters.protocols.JaegerThriftSpanConverter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.thrift.reporters.protocols.TestTServer;
import com.uber.jaeger.thriftjava.Batch;
import com.uber.jaeger.thriftjava.Process;

import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UdpSenderTest {
  static final String SERVICE_NAME = "test-sender";
  final String destHost = "localhost";
  int destPort;
  final int maxPacketSize = 1000;

  Tracer tracer;
  Reporter reporter;
  UdpSender sender;
  TestTServer server;

  private TestTServer startServer() throws Exception {
    TestTServer server = new TestTServer(0);
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
      jaegerSpan.log(msg);
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

    int processSize = sender.getSize(process);
    int spanSize = sender.getSize(span);

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
    assertEquals(expectedNumSpans, batch.getSpans().size());

    com.uber.jaeger.thriftjava.Span actualSpan = batch.getSpans().get(0);
    assertEquals(expectedSpan.context().getTraceId(), actualSpan.getTraceIdLow());
    assertEquals(0, actualSpan.getTraceIdHigh());
    assertEquals(expectedSpan.context().getSpanId(), actualSpan.getSpanId());
    assertEquals(0, actualSpan.getParentSpanId());
    assertTrue(actualSpan.references.isEmpty());
    assertEquals(expectedSpan.getOperationName(), actualSpan.getOperationName());
    assertEquals(4, batch.getProcess().getTags().size());
    assertEquals("hostname", batch.getProcess().getTags().get(0).getKey());
    assertEquals("jaeger.version", batch.getProcess().getTags().get(1).getKey());
    assertEquals("bar", batch.getProcess().getTags().get(2).getVStr());
    assertEquals("ip", batch.getProcess().getTags().get(3).getKey());
  }

}
