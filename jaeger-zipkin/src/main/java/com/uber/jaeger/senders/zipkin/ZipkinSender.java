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
import zipkin.reporter.Encoding;
import zipkin.reporter.internal.AwaitableCallback;
import zipkin.reporter.urlconnection.URLConnectionSender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This sends (TBinaryProtocol big-endian) encoded spans to a Zipkin Collector (usually a
 * zipkin-server).
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * reporter = new RemoteReporter(ZipkinSender.create("http://localhost:9411/api/v1/spans"));
 * tracer = new Tracer.Builder(serviceName, reporter, sampler)
 *                    ...
 * }</pre>
 *
 * <p>
 * See https://github.com/openzipkin/zipkin/tree/master/zipkin-server
 */
public final class ZipkinSender implements Sender {

  /**
   * @param endpoint The POST URL for zipkin's <a href="http://zipkin.io/zipkin-api/#/">v1 api</a>,
   * usually "http://zipkinhost:9411/api/v1/spans"
   * @return new sender
   */
  public static ZipkinSender create(String endpoint) {
    return new ZipkinSender(URLConnectionSender.create(endpoint));
  }

  /**
   * Use this to dispatch to an existing Zipkin sender which is configured for
   * {@link Encoding#THRIFT thrift encoding}.
   *
   * <p>
   * Ex. for Kafka ("io.zipkin.reporter:zipkin-sender-kafka08")
   *
   * <pre>{@code
   * sender = ZipkinSender.create(KafkaSender.create("192.168.99.100:9092"));
   * }</pre>
   *
   * @param delegate indicates an alternate sender library than {@link URLConnectionSender}
   * @return new sender
   */
  public static ZipkinSender create(zipkin.reporter.Sender delegate) {
    return new ZipkinSender(delegate);
  }

  final ThriftSpanEncoder encoder = new ThriftSpanEncoder();
  final zipkin.reporter.Sender delegate;
  final List<byte[]> spanBuffer;

  ZipkinSender(zipkin.reporter.Sender delegate) {
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
    int messageSizeOfNextSpan = delegate.messageSizeInBytes(Collections.singletonList(next));
    // don't enqueue something larger than we can drain
    if (Integer.compare(messageSizeOfNextSpan, delegate.messageMaxBytes()) > 0) {
      throw new SenderException(
          delegate.toString() + " received a span that was too large", null, 1);
    }

    spanBuffer.add(next); // speculatively add to the buffer so we can size it
    int nextSizeInBytes = delegate.messageSizeInBytes(spanBuffer);
    int includingNextVsMaxMessageSize =
        Integer.compare(nextSizeInBytes, delegate.messageMaxBytes());
    // If we can fit queued spans and the next into one message...
    if (includingNextVsMaxMessageSize <= 0) {

      // If there's still room, don't flush yet.
      if (includingNextVsMaxMessageSize < 0) {
        return 0;
      }
      // If we have exactly met the max message size, flush
      return flush();
    }

    // Otherwise, remove speculatively added span and flush until we have room for it.
    spanBuffer.remove(spanBuffer.size() - 1);
    int n;
    try {
      n = flush();
    } catch (SenderException e) {
      // +1 for the span not submitted in the buffer above
      throw new SenderException(e.getMessage(), e.getCause(), e.getDroppedSpanCount() + 1);
    }

    // Now that there's room, add the span as the only element in the buffer
    spanBuffer.add(next);
    return n;
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
