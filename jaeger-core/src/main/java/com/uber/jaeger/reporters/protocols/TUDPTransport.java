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

import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/*
 * A thrift transport for sending sending/receiving spans.
 */
public class TUDPTransport extends TTransport implements AutoCloseable {
  public static final int MAX_PACKET_SIZE = 65000;

  public final DatagramSocket socket;
  public byte[] receiveBuf;
  public int receiveOffSet = -1;
  public int receiveLength = 0;
  public ByteBuffer writeButter;

  // Create a UDP client for sending data to specific host and port
  public static TUDPTransport NewTUDPClient(String host, int port) {
    TUDPTransport t;
    try {
      t = new TUDPTransport();
      t.socket.connect(new InetSocketAddress(host, port));
    } catch (SocketException e) {
      throw new RuntimeException("TUDPTransport cannot connect: ", e);
    }
    return t;
  }

  // Create a UDP server for receiving data on specific host and port
  public static TUDPTransport NewTUDPServer(String host, int port)
      throws SocketException, UnknownHostException {
    TUDPTransport t = new TUDPTransport();
    t.socket.bind(new InetSocketAddress(host, port));
    return t;
  }

  private TUDPTransport() throws SocketException {
    this.socket = new DatagramSocket(null);
  }

  int getPort() {
    return socket.getLocalPort();
  }

  @Override
  public boolean isOpen() {
    return !this.socket.isClosed();
  }

  // noop as opened in constructor
  @Override
  public void open() throws TTransportException {}

  // close underlying socket
  @Override
  public void close() {
    this.socket.close();
  }

  @Override
  public int getBytesRemainingInBuffer() {
    // This forces thrift 0.9.2 to use its read_all path which works for reading from
    // sockets, since it fetches everything incrementally, rather than having a different
    // implementation based on some fixed buffer size.
    return 0;
  }

  @Override
  public int read(byte[] bytes, int offset, int len) throws TTransportException {
    if (!this.isOpen()) {
      throw new TTransportException(TTransportException.NOT_OPEN);
    }
    if (this.receiveOffSet == -1) {
      this.receiveBuf = new byte[MAX_PACKET_SIZE];
      DatagramPacket dg = new DatagramPacket(this.receiveBuf, MAX_PACKET_SIZE);
      try {
        this.socket.receive(dg);
      } catch (IOException e) {
        throw new TTransportException(
            TTransportException.UNKNOWN, "ERROR from underlying socket", e);
      }
      this.receiveOffSet = 0;
      this.receiveLength = dg.getLength();
    }
    int curDataSize = this.receiveLength - this.receiveOffSet;
    if (curDataSize <= len) {
      System.arraycopy(this.receiveBuf, this.receiveOffSet, bytes, offset, curDataSize);
      this.receiveOffSet = -1;
      return curDataSize;
    } else {
      System.arraycopy(this.receiveBuf, this.receiveOffSet, bytes, offset, len);
      this.receiveOffSet += len;
      return len;
    }
  }

  @Override
  public void write(byte[] bytes, int offset, int len) throws TTransportException {
    if (!this.isOpen()) {
      throw new TTransportException(TTransportException.NOT_OPEN);
    }
    if (this.writeButter == null) {
      this.writeButter = ByteBuffer.allocate(MAX_PACKET_SIZE);
    }
    if (this.writeButter.position() + len > MAX_PACKET_SIZE) {
      throw new TTransportException(
          TTransportException.UNKNOWN, "Message size too large: " + len + " > " + MAX_PACKET_SIZE);
    }
    this.writeButter.put(bytes, offset, len);
  }

  @Override
  public void flush() throws TTransportException {
    if (this.writeButter != null) {
      byte[] bytes = new byte[MAX_PACKET_SIZE];
      int len = this.writeButter.position();
      this.writeButter.flip();
      this.writeButter.get(bytes, 0, len);
      try {
        this.socket.send(new DatagramPacket(bytes, len));
      } catch (IOException e) {
        throw new TTransportException(
            TTransportException.UNKNOWN, "Cannot flush closed transport", e);
      } finally {
        this.writeButter = null;
      }
    }
  }

  @Override
  public String toString() {
    return "{\"_class\":\"TUDPTransport\", "
        + "\"socket\":"
        + (socket == null ? "null" : "\"" + socket + "\"")
        + ", "
        + "\"receiveOffSet\":\""
        + receiveOffSet
        + "\""
        + ", "
        + "\"receiveLength\":\""
        + receiveLength
        + "\""
        + "}";
  }
}
