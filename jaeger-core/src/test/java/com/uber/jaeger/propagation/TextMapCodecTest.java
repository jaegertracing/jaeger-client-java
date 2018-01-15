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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.hamcrest.collection.IsMapContaining;
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
  public void testGetDebugHeadersAsBaggageSingleHeader() {
    TextMapCodec codec = new TextMapCodec(false);

    /* testing key/value with no spaces */
    String value = "foo=bar";
    Map<String, String> result = codec.getDebugHeadersAsBaggage(value);

    assertNotNull(result);
    assertThat(result, IsMapContaining.hasEntry("foo", "bar"));
    assertThat(result.size(), is(1));

    /* testing value with spaces in it */
    value = "foo=bar baz ";
    result = codec.getDebugHeadersAsBaggage(value);

    assertNotNull(result);
    assertThat(result, IsMapContaining.hasEntry("foo", "bar baz"));
    assertThat(result.size(), is(1));

    /* testing key with spaces in it */
    value = "foo fum = bar ";
    result = codec.getDebugHeadersAsBaggage(value);

    assertNotNull(result);
    assertThat(result, IsMapContaining.hasEntry("foo fum", "bar"));
    assertThat(result.size(), is(1));

    /* testing key/value with spaces */
    value = "foo  fum  =bar baz ";
    result = codec.getDebugHeadersAsBaggage(value);

    assertNotNull(result);
    assertThat(result, IsMapContaining.hasEntry("foo  fum", "bar baz"));
    assertThat(result.size(), is(1));
  }

  @Test
  public void testGetDebugHeadersAsBaggageMultipleHeaders() {
    TextMapCodec codec = new TextMapCodec(false);

    /* testing key/value with no spaces */
    String value = "foo=bar,fee=baz";
    Map<String, String> result = codec.getDebugHeadersAsBaggage(value);

    assertNotNull(result);
    assertThat(result, IsMapContaining.hasEntry("foo", "bar"));
    assertThat(result, IsMapContaining.hasEntry("fee", "baz"));
    assertThat(result.size(), is(2));

    value = "foo=bar baz     , fee=     fum";
    result = codec.getDebugHeadersAsBaggage(value);

    assertNotNull(result);
    assertThat(result, IsMapContaining.hasEntry("foo", "bar baz"));
    assertThat(result, IsMapContaining.hasEntry("fee", "fum"));
    assertThat(result.size(), is(2));
  }

  @Test
  public void testGetDebugHeadersAsBaggageMalformedCases() {
    TextMapCodec codec = new TextMapCodec(false);

    String value = "=";
    Map<String, String> result = codec.getDebugHeadersAsBaggage(value);
    assertNull(result);

    value = "1=";
    result = codec.getDebugHeadersAsBaggage(value);
    assertNull(result);

    value = "1=, ,";
    result = codec.getDebugHeadersAsBaggage(value);
    assertNull(result);

    // trim after split on `,` will cause the first one to not get picked up
    value = "= , ,";
    result = codec.getDebugHeadersAsBaggage(value);
    assertNull(result);

    // multiple = causes length == 2 check to fail
    value = "====, ,=";
    result = codec.getDebugHeadersAsBaggage(value);
    assertNull(result);

    value = "== == , ,=";
    result = codec.getDebugHeadersAsBaggage(value);
    assertNull(result);

    // space around last = (on the key side) but nothing on value side will cause length == 1
    value = "== == , foo, =";
    result = codec.getDebugHeadersAsBaggage(value);
    assertNull(result);

    value = ",, ,       fum,,,,";
    result = codec.getDebugHeadersAsBaggage(value);
    assertNull(result);

    value = "1=, ,, foo=bar";
    result = codec.getDebugHeadersAsBaggage(value);
    assertNotNull(result);
    assertThat(result, IsMapContaining.hasEntry("foo", "bar"));
    assertThat(result.size(), is(1));
  }

  // FIXME: This test class is extremely incomplete and barely unittests TextMapCodec.

}
