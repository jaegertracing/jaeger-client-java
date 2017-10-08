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
package com.uber.jaeger.reporters.protocols;

import java.net.SocketException;
import java.net.UnknownHostException;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

public class ThriftUdpServerTransport extends TServerTransport {
  private ThriftUdpTransport transport;

  public ThriftUdpServerTransport(int localPort) throws SocketException, UnknownHostException {
    transport = ThriftUdpTransport.newThriftUdpServer("localhost", localPort);
  }

  int getPort() {
    return transport.getPort();
  }

  @Override
  public void listen() throws TTransportException {}

  @Override
  public void close() {
    if (transport.isOpen()) {
      transport.close();
    }
  }

  @Override
  protected TTransport acceptImpl() throws TTransportException {
    return transport;
  }

  @Override
  public void interrupt() {
    this.close();
  }
}
