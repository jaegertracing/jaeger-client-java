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
import static org.junit.Assert.assertNull;

import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import io.opentracing.tag.Tags;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TracerTagsTest {

  private enum SpanType {
    ROOT,
    CHILD,
    RPC_SERVER
  }

  // sentinel value is used to mark tags that should *not* be present
  private static final Object SENTINEL = new Object();

  private final SpanType spanType;
  private final Map<String, Object> expectedTags;

  public TracerTagsTest(SpanType spanType, Map<String, Object> expectedTags) {
    this.spanType = spanType;
    this.expectedTags = expectedTags;
  }

  @Parameterized.Parameters(name = "{index}: {0}")
  public static Collection<Object[]> data() {
    Tracer tracer = new Tracer.Builder("x", null, null).build();

    Map<String, Object> rootTags = new HashMap<>();
    rootTags.put("jaeger.version", SENTINEL);
    rootTags.put("jaeger.hostname", SENTINEL);
    rootTags.put("tracer.tag.str", SENTINEL);
    rootTags.put("sampler.type", "const");
    rootTags.put("sampler.param", true);

    Map<String, Object> sentinelTags = new HashMap<>();
    sentinelTags.put("jaeger.version", SENTINEL);
    sentinelTags.put("jaeger.hostname", SENTINEL);
    sentinelTags.put("tracer.tag.str", SENTINEL);
    sentinelTags.put("sampler.type", SENTINEL);
    sentinelTags.put("sampler.param", SENTINEL);

    List<Object[]> data = new ArrayList<>();
    data.add(new Object[] {SpanType.ROOT, rootTags});
    data.add(new Object[] {SpanType.CHILD, sentinelTags});
    data.add(new Object[] {SpanType.RPC_SERVER, sentinelTags});
    return data;
  }

  @Before
  public void setUp() throws Exception {}

  @Test
  public void testTracerTagsZipkin() throws Exception {
    InMemoryReporter spanReporter = new InMemoryReporter();
    Tracer tracer = new Tracer.Builder("x", spanReporter, new ConstSampler(true))
            .withZipkinSharedRpcSpan()
            .withTag("tracer.tag.str", "y")
            .build();

    Span span = (Span) tracer.buildSpan("root").startManual();
    if (spanType == SpanType.CHILD) {
      span = (Span) tracer.buildSpan("child").asChildOf(span).startManual();
    }
    if (spanType == SpanType.RPC_SERVER) {
      span =
          (Span)
              tracer
                  .buildSpan("rpc-server")
                  .asChildOf(span)
                  .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                  .startManual();
    }
    Map<String, Object> tags = span.getTags();
    for (String key : expectedTags.keySet()) {
      Object expectedValue = expectedTags.get(key);
      Object actualValue = tags.get(key);
      if (expectedValue == SENTINEL) {
        assertNull("Not expecting " + key + " for " + spanType, actualValue);
      } else {
        assertEquals("Expecting " + key + " for " + spanType, expectedValue, actualValue);
      }
    }
  }
}
