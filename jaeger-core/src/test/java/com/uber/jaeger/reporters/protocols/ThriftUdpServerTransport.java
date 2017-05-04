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
