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

package com.uber.jaeger.propagation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.uber.jaeger.SpanContext;

import io.opentracing.propagation.TextMap;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class TextMapCodecTest {

  @Test
  public void testBuilder()  {
    TextMapCodec codec = TextMapCodec.builder()
        .withUrlEncoding(true)
        .withSpanContextKey("jaeger-trace-id")
        .withBaggagePrefix("jaeger-baggage-")
        .build();
    assertNotNull(codec);
    String str = codec.toString();
    assertTrue(str.contains("contextKey=jaeger-trace-id"));
    assertTrue(str.contains("baggagePrefix=jaeger-baggage-"));
    assertTrue(str.contains("urlEncoding=true"));
  }

  @Test
  public void testWithoutBuilder() {
    TextMapCodec codec = new TextMapCodec(false);
    String str = codec.toString();
    assertTrue(str.contains("contextKey=uber-trace-id"));
    assertTrue(str.contains("baggagePrefix=uberctx-"));
    assertTrue(str.contains("urlEncoding=false"));
  }

  @Test
  public void testBuilderWithAnotherCodecInject()  {
    TextMapCodec codec = TextMapCodec.builder()
        .withSpanContextKey("jaeger-trace-id")
        .withCodec(new TestCodec())
        .build();
    assertNotNull(codec);

    SpanContext injectedContext = new SpanContext(1, 2, 3, (byte)0);
    DelegatingTextMap injectedTextMap = new DelegatingTextMap();
    codec.inject(injectedContext, injectedTextMap);

    // Verify that span context has been recorded in jaeger and the test headers
    assertEquals(injectedContext.contextAsString(), injectedTextMap.get(TestCodec.KEY));
    assertEquals(injectedContext.contextAsString(), injectedTextMap.get("jaeger-trace-id"));
  }

  @Test
  public void testBuilderWithAnotherCodecExtract()  {
    TextMapCodec codec = TextMapCodec.builder()
        .withCodec(new TestCodec())
        .build();
    assertNotNull(codec);

    SpanContext originalContext = new SpanContext(1, 2, 3, (byte)0);
    DelegatingTextMap extractedTextMap = new DelegatingTextMap();
    extractedTextMap.put(TestCodec.KEY, originalContext.contextAsString());
    SpanContext extractedContext = codec.extract(extractedTextMap);

    assertEquals(originalContext.contextAsString(), extractedContext.contextAsString());
  }

  @Test
  public void testBuilderWithAnotherCodecExtractJaegerTakePriority()  {
    TextMapCodec codec = TextMapCodec.builder()
        .withSpanContextKey("jaeger-trace-id")
        .withCodec(new TestCodec())
        .build();
    assertNotNull(codec);

    SpanContext testCodecContext = new SpanContext(1, 2, 3, (byte)0);
    SpanContext jaegerCodecContext = new SpanContext(4, 5, 6, (byte)0);
    DelegatingTextMap extractedTextMap = new DelegatingTextMap();
    extractedTextMap.put(TestCodec.KEY, testCodecContext.contextAsString());
    extractedTextMap.put("jaeger-trace-id", jaegerCodecContext.contextAsString());
    SpanContext extractedContext = codec.extract(extractedTextMap);

    assertEquals(jaegerCodecContext.contextAsString(), extractedContext.contextAsString());
  }

  static class TestCodec implements Codec<TextMap> {
    static final String KEY = "TestKey";

    @Override
    public void inject(SpanContext spanContext, TextMap carrier) {
      carrier.put(KEY, spanContext.contextAsString());
    }

    @Override
    public SpanContext extract(TextMap carrier) {
      if (carrier instanceof DelegatingTextMap) {
        String value = ((DelegatingTextMap)carrier).get(KEY);
        if (value != null) {
          return SpanContext.contextFromString(value);
        }
      }
      return null;
    }
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

    public String get(String key) {
      return delegate.get(key);
    }
  }
}
