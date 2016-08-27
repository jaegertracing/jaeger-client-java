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
package com.uber.jaeger.senders.zipkin;

import com.twitter.zipkin.thriftjava.Span;
import com.uber.jaeger.exceptions.SenderException;
import com.uber.jaeger.senders.Sender;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import zipkin.reporter.Encoding;
import zipkin.reporter.internal.AwaitableCallback;
import zipkin.reporter.urlconnection.URLConnectionSender;

/**
 * This sends (TBinaryProtocol big-endian) encoded spans to a Zipkin Collector (usually a
 * zipkin-server).
 *
 * <p>Example usage:
 * <pre>{@code
 * reporter = new RemoteReporter(ZipkinSender.create("http://localhost:9411/api/v1/spans"));
 * tracer = new Tracer.Builder(serviceName, reporter, sampler)
 *                    ...
 * }</pre>
 *
 * <p>See https://github.com/openzipkin/zipkin/tree/master/zipkin-server
 */
public final class ZipkinSender implements Sender {

  /**
   * The POST URL for zipkin's <a href="http://zipkin.io/zipkin-api/#/">v1 api</a>, usually
   * "http://zipkinhost:9411/api/v1/spans"
   */
  public static ZipkinSender create(String endpoint) {
    return new ZipkinSender(URLConnectionSender.create(endpoint));
  }

  /**
   * Use this to dispatch to an existing Zipkin sender which is configured for {@link
   * Encoding#THRIFT thrift encoding}.
   *
   * <p>Ex. for Kafka ("io.zipkin.reporter:zipkin-sender-kafka08")
   * <pre>{@code
   * sender = ZipkinSender.create(KafkaSender.create("192.168.99.100:9092"));
   * }</pre>
   */
  public static ZipkinSender create(zipkin.reporter.Sender<byte[]> delegate) {
    return new ZipkinSender(delegate);
  }

  final ThriftSpanEncoder encoder = new ThriftSpanEncoder();
  final zipkin.reporter.Sender<byte[]> delegate;
  final List<byte[]> spanBuffer;

  int queuedSizeInBytes;

  ZipkinSender(zipkin.reporter.Sender<byte[]> delegate) {
    this.delegate = delegate;
    this.spanBuffer = new ArrayList<byte[]>();
  }

  /*
  * Adds spans to an internal queue that flushes them as udp packets later.
  * This function does not need to be synchronized because the reporter creates
  * a single thread that calls this append function
  */
  @Override
  public int append(Span span) throws SenderException {
    byte[] next = encoder.encode(span);
    int nextSizeInBytes = encoder.sizeInBytes(next);
    // don't enqueue something larger than we can doDrain
    if (compareToMaxMessageSize(1, nextSizeInBytes) > 0) {
      throw new SenderException(delegate.toString() + " received a span that was too large",
          null, 1);
    }

    int includingNextVsMaxMessageSize = compareToMaxMessageSize(
        spanBuffer.size() + 1,
        queuedSizeInBytes + nextSizeInBytes
    );
    // If we can fit queued spans and the next into one message...
    if (includingNextVsMaxMessageSize <= 0) {
      spanBuffer.add(next);
      queuedSizeInBytes += nextSizeInBytes;

      // If there's still room, don't flush yet.
      if (includingNextVsMaxMessageSize < 0) {
        return 0;
      }
      // If we have exactly met the max message size, flush
      return flush();
    }

    // Otherwise, we have to flush existing spans to make room for the next.
    int n;
    try {
      n = flush();
    } catch (SenderException e) {
      // +1 for the span not submitted in the buffer above
      throw new SenderException(e.getMessage(), e.getCause(), e.getDroppedSpanCount() + 1);
    }

    // Now that there's room, add the span as the only element in the buffer
    spanBuffer.add(next);
    queuedSizeInBytes = nextSizeInBytes;
    return n;
  }

  private int compareToMaxMessageSize(int spanCount, int spanBytes) {
    return Integer.compare(delegate.messageEncoding().overheadInBytes(spanCount) + spanBytes,
        delegate.messageMaxBytes());
  }

  @Override
  public int flush() throws SenderException {
    if (spanBuffer.isEmpty()) {
      return 0;
    }

    AwaitableCallback captor = new AwaitableCallback();
    int n = spanBuffer.size();
    try {
      delegate.sendSpans(spanBuffer, captor);
      captor.await();
    } catch (RuntimeException e) {
      throw new SenderException("Failed to flush spans.", e, n);
    } finally {
      spanBuffer.clear();
      queuedSizeInBytes = 0;
      return n;
    }
  }

  @Override
  public int close() throws SenderException {
    int n = flush();
    try {
      delegate.close();
    } catch (IOException e) {
      throw new SenderException("Failed to close " + delegate, e, n);
    }
    return n;
  }
}
