/*
 * Copyright 2021, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.jaegertracing.internal.propagation;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import io.jaegertracing.internal.JaegerSpanContext;
import io.opentracing.propagation.TextMapAdapter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Mostly copied from OpenTelemetry Java SDK
 * https://github.com/open-telemetry/opentelemetry-java/blob/v1.5.0/api/all/src/test/java/io/opentelemetry/api/baggage/propagation
 */
public class W3CBaggagePropagatorTest {

  @Test
  public void extract_key_duplicate() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("key=value1,key=value2");

    Map<String, String> expected = new HashMap<>();
    expected.put("key", "value2");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_key_leadingSpaces() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("  key=value1");

    Map<String, String> expected = new HashMap<>();
    expected.put("key", "value1");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_key_trailingSpaces() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("key     =value1");

    Map<String, String> expected = new HashMap<>();
    expected.put("key", "value1");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_key_onlySpaces() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("   =value1");

    assertThat(result, is(Collections.emptyMap()));
  }

  @Test
  public void extract_key_withInnerSpaces() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("ke y=value1");

    assertThat(result, is(Collections.emptyMap()));
  }

  @Test
  public void extract_key_withSeparators() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("ke?y=value1");

    assertThat(result, is(Collections.emptyMap()));
  }

  @Test
  public void extract_key_singleValid_multipleInvalid() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage(
        "ke<y=value1, ;sss,key=value;meta1=value1;meta2=value2,ke(y=value;meta=val ");

    Map<String, String> expected = new HashMap<>();
    expected.put("key", "value");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_value_leadingSpaces() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("  key=  value1");

    Map<String, String> expected = new HashMap<>();
    expected.put("key", "value1");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_value_trailingSpaces() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("key=value1      ");

    Map<String, String> expected = new HashMap<>();
    expected.put("key", "value1");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_value_trailingSpaces_withMetadata() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("key=value1      ;meta1=meta2");

    Map<String, String> expected = new HashMap<>();
    expected.put("key", "value1");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_value_empty() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("key1=");

    assertThat(result, is(Collections.emptyMap()));
  }

  @Test
  public void extract_value_empty_withMetadata() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("key1=;metakey=metaval");

    assertThat(result, is(Collections.emptyMap()));
  }

  @Test
  public void extract_value_onlySpaces() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("key1=     ");

    assertThat(result, is(Collections.emptyMap()));
  }

  @Test
  public void extract_value_withInnerSpaces() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("key=valu e1");

    assertThat(result, is(Collections.emptyMap()));
  }

  @Test
  public void extract_value_withSeparators() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("key=val\\ue1");

    assertThat(result, is(Collections.emptyMap()));
  }

  @Test
  public void extract_value_multiple_leadingSpaces() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("key=  value1,key1=val");

    Map<String, String> expected = new HashMap<>();
    expected.put("key1", "val");
    expected.put("key", "value1");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_value_multiple_trailingSpaces() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("key=value1      ,key1=val");

    Map<String, String> expected = new HashMap<>();
    expected.put("key1", "val");
    expected.put("key", "value1");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_value_multiple_empty_withMeEtadata() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("key1=;metakey=metaval,key1=val");

    Map<String, String> expected = new HashMap<>();
    expected.put("key1", "val");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_value_multiple_empty() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("key1=,key1=val");

    Map<String, String> expected = new HashMap<>();
    expected.put("key1", "val");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_value_multiple_onlySpaces() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("key1=     ,key1=val");

    Map<String, String> expected = new HashMap<>();
    expected.put("key1", "val");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_value_multiple_withInnerSpaces() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("key=valu e1,key1=val");

    Map<String, String> expected = new HashMap<>();
    expected.put("key1", "val");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_value_multiple_withSeparators() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("key=val\\ue1,key1=val");

    Map<String, String> expected = new HashMap<>();
    expected.put("key1", "val");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_header_empty() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("");
    assertThat(result, is(Collections.emptyMap()));
  }

  @Test
  public void extract_member_single() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("key=value");

    Map<String, String> expected = new HashMap<>();
    expected.put("key", "value");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_member_multi() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator.extractBaggage("key1=value1,key2=value2");

    Map<String, String> expected = new HashMap<>();
    expected.put("key1", "value1");
    expected.put("key2", "value2");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_member_single_withMetadata() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    Map<String, String> result = propagator
        .extractBaggage("key=value;metadata-key=value;othermetadata");

    Map<String, String> expected = new HashMap<>();
    expected.put("key", "value");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_member_fullComplexities() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    final Map<String, String> result = propagator.extractBaggage(
        "key1= value1; metadata-key = value; othermetadata, " + "key2 =value2 , key3 =\tvalue3 ; ");

    Map<String, String> expected = new HashMap<>();
    expected.put("key1", "value1");
    expected.put("key2", "value2");
    expected.put("key3", "value3");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_member_someInvalid() {
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;

    final Map<String, String> result = propagator.extractBaggage(
        "key1= v;alsdf;-asdflkjasdf===asdlfkjadsf ,,a sdf9asdf-alue1; metadata-key = "
            + "value; othermetadata, key2 =value2, key3 =\tvalue3 ; ");

    Map<String, String> expected = new HashMap<>();
    expected.put("key1", "v");
    expected.put("key2", "value2");
    expected.put("key3", "value3");
    assertThat(result, is(expected));
  }

  @Test
  public void extract_null() {
    assertNull(W3CBaggagePropagator.INSTANCE.extractBaggage(null));
  }

  @Test
  public void inject_noBaggage() {
    Map<String, String> carrier = new HashMap<>();
    TextMapAdapter textMap = new TextMapAdapter(carrier);
    JaegerSpanContext spanContext = new JaegerSpanContext(0, 0, 0, 0, (byte) 0);
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;
    propagator.injectBaggage(spanContext, textMap);
    assertThat(carrier, is(Collections.emptyMap()));
  }

  @Test
  public void inject() {
    JaegerSpanContext spanContext = new JaegerSpanContext(0, 0, 0, 0, (byte) 0)
        .withBaggageItem("key1", "value1")
        .withBaggageItem("key2", "value2; something=looks-like-meta");
    W3CBaggagePropagator propagator = W3CBaggagePropagator.INSTANCE;
    Map<String, String> carrier = new HashMap<>();
    TextMapAdapter textMap = new TextMapAdapter(carrier);
    propagator.injectBaggage(spanContext, textMap);
    assertThat(carrier, is(Collections.singletonMap("baggage",
        "key1=value1,key2=value2%3B+something%3Dlooks-like-meta")));
  }

  @Test
  public void inject_nullContext() {
    Map<String, String> carrier = new HashMap<>();
    TextMapAdapter textMap = new TextMapAdapter(carrier);
    W3CBaggagePropagator.INSTANCE.injectBaggage(null, textMap);
    assertThat(carrier, is(Collections.emptyMap()));
  }

}
