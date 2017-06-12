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

import com.uber.jaeger.agent.thrift.Agent;
import com.uber.jaeger.exceptions.SenderException;
import com.uber.jaeger.reporters.protocols.ThriftUdpTransport;
import com.uber.jaeger.thriftjava.Batch;
import com.uber.jaeger.thriftjava.Process;
import java.util.List;
import lombok.ToString;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocolFactory;

@ToString(exclude = {"agentClient"})
public class UdpSender extends ThriftSender {
  private static final TProtocolFactory PROTOCOL_FACTORY = new TCompactProtocol.Factory();
  public static final String DEFAULT_AGENT_UDP_HOST = "localhost";
  public static final int DEFAULT_AGENT_UDP_COMPACT_PORT = 6831;

  private Agent.Client agentClient;
  private ThriftUdpTransport udpTransport;

  public UdpSender(String host, int port, int maxPacketSize) {
    super(PROTOCOL_FACTORY, maxPacketSize);

    if (host == null || host.length() == 0) {
      host = DEFAULT_AGENT_UDP_HOST;
    }

    if (port == 0) {
      port = DEFAULT_AGENT_UDP_COMPACT_PORT;
    }

    udpTransport = ThriftUdpTransport.newThriftUdpClient(host, port);
    agentClient = new Agent.Client(PROTOCOL_FACTORY.getProtocol(udpTransport));
  }

  @Override
  public void send(Process process, List<com.uber.jaeger.thriftjava.Span> spans) throws TException {
    agentClient.emitBatch(new Batch(process, spans));
  }

  @Override
  public int close() throws SenderException {
    try {
      return super.close();
    } finally {
      udpTransport.close();
    }
  }
}
