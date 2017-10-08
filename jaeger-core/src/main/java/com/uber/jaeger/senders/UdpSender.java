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

import com.uber.jaeger.agent.thrift.Agent;
import com.uber.jaeger.exceptions.SenderException;
import com.uber.jaeger.reporters.protocols.ThriftUdpTransport;
import com.uber.jaeger.thriftjava.Batch;
import com.uber.jaeger.thriftjava.Process;
import java.util.List;
import lombok.ToString;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TCompactProtocol;

@ToString(exclude = {"agentClient"})
public class UdpSender extends ThriftSender {
  public static final String DEFAULT_AGENT_UDP_HOST = "localhost";
  public static final int DEFAULT_AGENT_UDP_COMPACT_PORT = 6831;

  private Agent.Client agentClient;
  private ThriftUdpTransport udpTransport;

  public UdpSender(String host, int port, int maxPacketSize) {
    super(new TCompactProtocol.Factory(), maxPacketSize);

    if (host == null || host.length() == 0) {
      host = DEFAULT_AGENT_UDP_HOST;
    }

    if (port == 0) {
      port = DEFAULT_AGENT_UDP_COMPACT_PORT;
    }

    udpTransport = ThriftUdpTransport.newThriftUdpClient(host, port);
    agentClient = new Agent.Client(protocolFactory.getProtocol(udpTransport));
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
