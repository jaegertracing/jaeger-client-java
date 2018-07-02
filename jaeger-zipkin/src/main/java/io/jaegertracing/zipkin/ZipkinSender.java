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

package io.jaegertracing.zipkin;

import static com.twitter.zipkin.thriftjava.zipkincoreConstants.CLIENT_RECV;
import static com.twitter.zipkin.thriftjava.zipkincoreConstants.CLIENT_SEND;
import static com.twitter.zipkin.thriftjava.zipkincoreConstants.SERVER_RECV;
import static com.twitter.zipkin.thriftjava.zipkincoreConstants.SERVER_SEND;

import com.twitter.zipkin.thriftjava.Annotation;
import com.twitter.zipkin.thriftjava.BinaryAnnotation;
import com.twitter.zipkin.thriftjava.Endpoint;
import com.twitter.zipkin.thriftjava.Span;
import com.twitter.zipkin.thriftjava.zipkincoreConstants;
import io.jaegertracing.internal.JaegerSpan;
import io.jaegertracing.internal.exceptions.SenderException;
import io.jaegertracing.spi.Sender;
import io.jaegertracing.zipkin.internal.ThriftSpanConverter;
import io.jaegertracing.zipkin.internal.ThriftSpanEncoder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.ToString;
import zipkin2.codec.Encoding;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * This sends (TBinaryProtocol big-endian) encoded spans to a Zipkin Collector (usually a
 * zipkin-server).  If you want to send newer Zipkin V2 spans in protocols other than Thrift,
 * see {@link ZipkinV2Reporter ZipkinV2Reporter}.
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * reporter = new RemoteReporter(ZipkinSender.create("http://localhost:9411/api/v1/spans"));
 * tracer = new JaegerTracer.Builder(serviceName, reporter, sampler)
 *                    ...
 * }</pre>
 *
 * <p>
 * See https://github.com/openzipkin/zipkin/tree/master/zipkin-server
 */
@ToString(exclude = {"spanBuffer", "encoder"})
public final class ZipkinSender implements Sender {
  static final Set<String> CORE_ANNOTATIONS = new LinkedHashSet<String>(
      Arrays.asList(CLIENT_SEND, CLIENT_RECV, SERVER_SEND, SERVER_RECV));

  /**
   * @param endpoint The POST URL for zipkin's <a href="http://zipkin.io/zipkin-api/#/">v1 api</a>,
   * usually "http://zipkinhost:9411/api/v1/spans"
   * @return new sender
   */
  public static ZipkinSender create(String endpoint) {
    return new ZipkinSender(
        URLConnectionSender.newBuilder().encoding(Encoding.THRIFT).endpoint(endpoint).build());
  }

  /**
   * Use this to dispatch to an existing Zipkin sender which is configured for
   * {@link Encoding#THRIFT thrift encoding}.
   *
   * <p>
   * Ex. for Kafka ("io.zipkin2.reporter:zipkin-sender-kafka11")
   *
   * <pre>{@code
   * sender = ZipkinSender.create(
   *     KafkaSender.newBuilder()
   *                .encoding(Encoding.THRIFT)
   *                .bootstrapServers("192.168.99.100:9092")
   *                .build()
   * );
   * }</pre>
   *
   * @param delegate indicates an alternate sender library than {@link URLConnectionSender}
   * @return new sender
   */
  public static ZipkinSender create(zipkin2.reporter.Sender delegate) {
    if (delegate.encoding() != Encoding.THRIFT) {
      throw new IllegalArgumentException("Sender.encoding != THRIFT");
    }
    return new ZipkinSender(delegate);
  }

  final ThriftSpanEncoder encoder = new ThriftSpanEncoder();
  final zipkin2.reporter.Sender delegate;
  final List<byte[]> spanBuffer;

  ZipkinSender(zipkin2.reporter.Sender delegate) {
    this.delegate = delegate;
    this.spanBuffer = new ArrayList<byte[]>();
  }

  /*
   * Adds spans to an internal queue that flushes them as udp packets later.
   * This function does not need to be synchronized because the reporter creates
   * a single thread that calls this append function
   */
  @Override
  public int append(JaegerSpan span) throws SenderException {
    byte[] next = encoder.encode(backFillHostOnAnnotations(ThriftSpanConverter.convertSpan(span)));
    int messageSizeOfNextSpan = delegate.messageSizeInBytes(Collections.singletonList(next));
    // don't enqueue something larger than we can drain
    if (messageSizeOfNextSpan > delegate.messageMaxBytes()) {
      throw new SenderException(
          delegate.toString() + " received a span that was too large", null, 1);
    }

    spanBuffer.add(next); // speculatively add to the buffer so we can size it
    int nextSizeInBytes = delegate.messageSizeInBytes(spanBuffer);
    // If we can fit queued spans and the next into one message...
    if (nextSizeInBytes <= delegate.messageMaxBytes()) {

      // If there's still room, don't flush yet.
      if (nextSizeInBytes < delegate.messageMaxBytes()) {
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

    int n = spanBuffer.size();
    try {
      delegate.sendSpans(spanBuffer).execute();
    } catch (RuntimeException e) {
      throw new SenderException("Failed to flush spans.", e, n);
    } catch (IOException e) {
      throw new SenderException("Failed to flush spans.", e, n);
    } finally {
      spanBuffer.clear();
    }
    return n;
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

  // serviceName/host is needed on annotations so zipkin can query
  // see https://github.com/jaegertracing/jaeger-client-java/pull/75 for more info
  static Span backFillHostOnAnnotations(Span span) {
    Endpoint host = getServiceEndpoint(span);

    if (host != null) {
      for (BinaryAnnotation binaryAnnotation : span.getBinary_annotations()) {
        if (!binaryAnnotation.isSetHost()) {
          binaryAnnotation.setHost(host);
        }
      }

      for (Annotation annotation : span.getAnnotations()) {
        if (!annotation.isSetHost()) {
          annotation.setHost(host);
        }
      }
    }

    return span;
  }

  static Endpoint getServiceEndpoint(Span span) {
    for (Annotation annotation : span.getAnnotations()) {
      if (CORE_ANNOTATIONS.contains(annotation.getValue()) && annotation.isSetHost()) {
        return annotation.getHost();
      }
    }

    for (BinaryAnnotation binaryAnnotation : span.getBinary_annotations()) {
      if (zipkincoreConstants.LOCAL_COMPONENT.equals(binaryAnnotation.getKey())) {
        return binaryAnnotation.getHost();
      }
    }

    return null;
  }
}
