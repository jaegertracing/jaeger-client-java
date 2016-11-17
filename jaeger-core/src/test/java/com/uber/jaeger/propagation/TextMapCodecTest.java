package com.uber.jaeger.propagation;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextMapCodecTest {

    @Test
    public void testBuilder()  {
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

    @Test
    public void testWithoutBuilder() {
        TextMapCodec codec = new TextMapCodec(false);
        String str = codec.toString();
        assertTrue(str.contains("contextKey=uber-trace-id"));
        assertTrue(str.contains("baggagePrefix=uberctx-"));
        assertTrue(str.contains("urlEncoding=false"));
    }

}
