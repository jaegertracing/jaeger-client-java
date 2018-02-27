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

import static org.junit.Assert.assertNull;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import com.uber.jaeger.SpanContext;
import io.opentracing.propagation.TextMap;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class B3TextMapCodecResiliencyTest {

  private B3TextMapCodec sut = new B3TextMapCodec();

  @DataProvider
  public static Object[] maliciousInputs() {
    return new Object[] {
        "abcbdabcbdabcbdabcbdabcbdabcbdabcbdabcbdabcbdabcbdabcbd",
        "",
        "ABCDEF",
    };
  }

  @Test
  @UseDataProvider("maliciousInputs")
  public void shouldFallbackWhenMaliciousInput(String maliciousInput) {
    String validInput = "ffffffffffffffffffffffffffffffff";
    TextMap maliciousCarrier = new B3TextMapCodecTest.DelegatingTextMap();
    maliciousCarrier.put(B3TextMapCodec.TRACE_ID_NAME, validInput);
    maliciousCarrier.put(B3TextMapCodec.SPAN_ID_NAME, validInput);
    maliciousCarrier.put(B3TextMapCodec.PARENT_SPAN_ID_NAME, validInput);

    // everything is valid, except for one of the headers at a time:
    for (String header : Arrays.asList(
        B3TextMapCodec.TRACE_ID_NAME,
        B3TextMapCodec.SPAN_ID_NAME,
        B3TextMapCodec.PARENT_SPAN_ID_NAME)) {
      maliciousCarrier.put(header, maliciousInput);
      //when
      SpanContext extract = sut.extract(maliciousCarrier);

      //then
      assertNull(extract);

      maliciousCarrier.put(header, validInput);
    }
  }
}
