package com.uber.jaeger.propagation;

import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.isNotNull;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import com.uber.jaeger.SpanContext;
import io.opentracing.propagation.TextMap;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class B3TextMapCodecResiliencyTest {

  private B3TextMapCodec sut = new B3TextMapCodec();

  @DataProvider
  public static Object[] maliciousInputs(){
    return new Object[]{
      "abcbdabcbdabcbdabcbdabcbdabcbdabcbdabcbdabcbdabcbdabcbd",
      "",
      "ABCDEF"
    };
  }


  @Test
  @UseDataProvider("maliciousInputs")
  public void shouldFallbackWhenMaliciousInput(String maliciousInput) {
    TextMap maliciousCarrier = new B3TextMapCodecTest.DelegatingTextMap();
    maliciousCarrier.put(B3TextMapCodec.TRACE_ID_NAME, maliciousInput);
    maliciousCarrier.put(B3TextMapCodec.SPAN_ID_NAME, maliciousInput);
    maliciousCarrier.put(B3TextMapCodec.PARENT_SPAN_ID_NAME, maliciousInput);

    //when
    SpanContext extract = sut.extract(maliciousCarrier);

    //then
    assertThat(extract, isNotNull());
  }
}









