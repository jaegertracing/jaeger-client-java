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
package com.uber.jaeger.propagation.b3;

import static com.uber.jaeger.propagation.b3.B3TextMapCodec.SPAN_ID_NAME;
import static com.uber.jaeger.propagation.b3.B3TextMapCodec.TRACE_ID_NAME;
import static org.junit.Assert.assertEquals;

import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;
import com.github.kristofa.brave.http.*;
import com.uber.jaeger.SpanContext;
import io.opentracing.propagation.TextMap;
import java.net.URI;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class B3TextMapCodecTest {
  static final byte SAMPLED = 1;

  B3TextMapCodec b3Codec = new B3TextMapCodec();

  @Test
  public void testExtract_rootSpan() throws Exception {
    SpanId spanId = SpanId.builder().spanId(1L).traceId(1L).parentId(null).build();
    TextMap textMap = makeRequestWithSpanId(spanId);

    SpanContext context = b3Codec.extract(textMap);

    assertEquals(1, context.getTraceID());
    assertEquals(0, context.getParentID()); // parentID==0 means root span
    assertEquals(1, context.getSpanID());
    assertEquals(1, context.getFlags()); // sampled
  }

  @Test
  public void downgrades128BitTraceIdToLower64Bits() throws Exception {
    String hex128Bits = "463ac35c9f6413ad48485a3953bb6124";
    String lower64Bits = "48485a3953bb6124";

    DelegatingTextMap textMap = new DelegatingTextMap();
    textMap.put(TRACE_ID_NAME, hex128Bits);
    textMap.put(SPAN_ID_NAME, lower64Bits);

    SpanContext context = b3Codec.extract(textMap);

    assertEquals(HexCodec.lowerHexToUnsignedLong(lower64Bits), context.getTraceID());
    assertEquals(HexCodec.lowerHexToUnsignedLong(lower64Bits), context.getSpanID());
  }

  @Test
  public void testExtract_childSpan() throws Exception {
    SpanId spanId = SpanId.builder().spanId(2L).traceId(1L).parentId(1L).build();
    TextMap textMap = makeRequestWithSpanId(spanId);

    SpanContext context = b3Codec.extract(textMap);

    assertEquals(1, context.getTraceID());
    assertEquals(1, context.getParentID());
    assertEquals(2, context.getSpanID());
    assertEquals(1, context.getFlags()); // sampled
  }

  @Test
  public void testExtract_unsampled() throws Exception {
    TextMap textMap = makeRequestWithSpanId(null);

    SpanContext context = b3Codec.extract(textMap);

    assertEquals(null, context);
  }

  @Test
  public void testInject_rootSpan() throws Exception {
    DelegatingTextMap textMap = new DelegatingTextMap();
    b3Codec.inject(new SpanContext(1, 1, 0, SAMPLED), textMap);

    TraceData data = takeRequestFromTextMap(textMap);

    assertEquals(1, data.getSpanId().traceId);
    assertEquals(null, data.getSpanId().nullableParentId());
    assertEquals(1, data.getSpanId().spanId);
    assertEquals(Boolean.TRUE, data.getSample());
  }

  @Test
  public void testInject_childSpan() throws Exception {
    DelegatingTextMap textMap = new DelegatingTextMap();
    b3Codec.inject(new SpanContext(1, 2, 1, SAMPLED), textMap);

    TraceData data = takeRequestFromTextMap(textMap);

    assertEquals(1, data.getSpanId().traceId);
    assertEquals(Long.valueOf(1), data.getSpanId().nullableParentId());
    assertEquals(2, data.getSpanId().spanId);
    assertEquals(Boolean.TRUE, data.getSample());
  }

  @Test
  public void testInject_unsampled() throws Exception {
    DelegatingTextMap textMap = new DelegatingTextMap();
    b3Codec.inject(new SpanContext(1, 1, 0, (byte) 0 /* unsampled */), textMap);

    TraceData data = takeRequestFromTextMap(textMap);

    assertEquals(Boolean.FALSE, data.getSample());
  }

  TextMap makeRequestWithSpanId(SpanId spanId) {
    final DelegatingTextMap textMap = new DelegatingTextMap();
    HttpClientRequest request =
        new HttpClientRequest() {

          @Override
          public URI getUri() {
            return URI.create("/");
          }

          @Override
          public String getHttpMethod() {
            return "GET";
          }

          @Override
          public void addHeader(String header, String value) {
            textMap.put(header, value);
          }
        };
    new HttpClientRequestAdapter(request, new DefaultSpanNameProvider()).addSpanIdToRequest(spanId);
    return textMap;
  }

  TraceData takeRequestFromTextMap(final DelegatingTextMap textMap) {
    HttpServerRequest request =
        new HttpServerRequest() {

          @Override
          public String getHttpHeaderValue(String headerName) {
            return textMap.delegate.get(headerName);
          }

          @Override
          public URI getUri() {
            return URI.create("/");
          }

          @Override
          public String getHttpMethod() {
            return "GET";
          }
        };
    return new HttpServerRequestAdapter(request, new DefaultSpanNameProvider()).getTraceData();
  }

  static class DelegatingTextMap implements TextMap {
    final Map<String, String> delegate = new LinkedHashMap<>();

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
      return delegate.entrySet().iterator();
    }

    @Override
    public void put(String key, String value) {
      delegate.put(key, value);
    }
  }
}
