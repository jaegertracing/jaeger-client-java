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

package com.uber.jaeger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapExtractAdapter;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class PropagationTest {
  @Test
  public void testDebugCorrelationId() {
    Tracer tracer =
        new Tracer.Builder("test", new InMemoryReporter(), new ConstSampler(true)).build();
    Map<String, String> headers = new HashMap<>();
    headers.put(Constants.DEBUG_ID_HEADER_KEY, "Coraline");
    TextMap carrier = new TextMapExtractAdapter(headers);
    SpanContext spanContext = (SpanContext) tracer.extract(Format.Builtin.TEXT_MAP, carrier);
    assertTrue(spanContext.isDebugIdContainerOnly());
    assertEquals("Coraline", spanContext.getDebugId());
    Span span = (Span) tracer.buildSpan("span").asChildOf(spanContext).start();
    spanContext = (SpanContext) span.context();
    assertTrue(spanContext.isSampled());
    assertTrue(spanContext.isDebug());
    assertEquals("Coraline", span.getTags().get(Constants.DEBUG_ID_HEADER_KEY));
  }
}
