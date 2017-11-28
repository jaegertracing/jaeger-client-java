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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextMapCodecTest {

  @Test
  public void testBuilder()  {
    TextMapCodec codec = TextMapCodec.builder()
        .withUrlEncoding(true)
        .withSpanContextKey("jaeger-trace-id")
        .withBaggagePrefix("jaeger-baggage-")
        .withB3(true)
        .build();
    assertNotNull(codec);
    String str = codec.toString();
    assertTrue(str.contains("contextKey=jaeger-trace-id"));
    assertTrue(str.contains("baggagePrefix=jaeger-baggage-"));
    assertTrue(str.contains("urlEncoding=true"));
    assertTrue(str.contains("b3=true"));
  }

  @Test
  public void testWithoutBuilder() {
    TextMapCodec codec = new TextMapCodec(false);
    String str = codec.toString();
    assertTrue(str.contains("contextKey=uber-trace-id"));
    assertTrue(str.contains("baggagePrefix=uberctx-"));
    assertTrue(str.contains("urlEncoding=false"));
    assertTrue(str.contains("b3=false"));
  }

}
