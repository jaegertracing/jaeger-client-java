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

package com.uber.jaeger.reporters.protocols;

import com.uber.jaeger.agent.thrift.Agent;
import com.uber.jaeger.thriftjava.Batch;
import com.uber.jaeger.thriftjava.Span;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;

public class TestTServer implements Runnable {
  TServer server;
  InMemorySpanServerHandler handler;
  ThriftUdpServerTransport transport;

  public TestTServer(int port) throws SocketException, UnknownHostException {
    handler = new InMemorySpanServerHandler();
    transport = new ThriftUdpServerTransport(port);
    server =
        new TSimpleServer(
            new TServer.Args(transport)
                .protocolFactory(new TCompactProtocol.Factory())
                .processor(new Agent.Processor<>(handler)));
  }

  public int getPort() {
    return transport.getPort();
  }

  @Override
  public void run() {
    server.serve();
  }

  public void close() {
    server.stop();
  }

  public Batch getBatch(int expectedSpans, int timeout) throws Exception {

    Batch batch = new Batch().setSpans(new ArrayList<Span>());
    long expire = timeout + System.currentTimeMillis();
    while (System.currentTimeMillis() < expire) {
      Batch receivedBatch = handler.getBatch();
      if (receivedBatch != null && receivedBatch.getSpans() != null) {
        batch.getSpans().addAll(receivedBatch.getSpans());
        batch.setProcess(receivedBatch.getProcess());
      }

      if (batch.spans.size() >= expectedSpans) {
        return batch;
      }

      Thread.sleep(1);
    }

    return batch;
  }
}
