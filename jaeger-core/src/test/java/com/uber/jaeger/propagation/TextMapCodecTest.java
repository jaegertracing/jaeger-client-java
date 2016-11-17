package com.uber.jaeger.propagation;

import org.junit.Test;

import static org.junit.Assert.*;


public class TextMapCodecTest {

    @Test
    public void testBuilder() throws Exception {
        TextMapCodec codec = TextMapCodec.builder()
                .withURLEncoding(true)
                .withSpanContextKey("jaeger-trace-id")
                .withBaggagePrefix("jaeger-baggage-")
                .build();
        assertNotNull(codec);
        String str = codec.toString();
        assertTrue(str.contains("contextKey=jaeger-trace-id"));
        assertTrue(str.contains("baggagePrefix=jaeger-baggage-"));
        assertTrue(str.contains("urlEncoding=true"));
    }

}