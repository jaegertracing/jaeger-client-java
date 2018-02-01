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

package com.uber.jaeger.propagation.b3;

import static com.uber.jaeger.propagation.b3.B3TextMapCodec.SPAN_ID_NAME;
import static com.uber.jaeger.propagation.b3.B3TextMapCodec.TRACE_ID_NAME;
import static org.junit.Assert.assertEquals;

import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpClientRequest;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.HttpServerRequest;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
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

    assertEquals(1, context.getTraceId());
    assertEquals(0, context.getParentId()); // parentID==0 means root span
    assertEquals(1, context.getSpanId());
    assertEquals(1, context.getFlags()); // sampled
  }

  @Test
  public void testExtract_childSpan() throws Exception {
    SpanId spanId = SpanId.builder().spanId(2L).traceId(1L).parentId(1L).build();
    TextMap textMap = makeRequestWithSpanId(spanId);

    SpanContext context = b3Codec.extract(textMap);

    assertEquals(1, context.getTraceId());
    assertEquals(1, context.getParentId());
    assertEquals(2, context.getSpanId());
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
