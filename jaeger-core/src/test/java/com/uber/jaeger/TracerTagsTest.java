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

import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import io.opentracing.tag.Tags;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
    String hostname = tracer.getHostName();

    Map<String, Object> rootTags = new HashMap<>();
    rootTags.put("jaeger.version", Tracer.VERSION);
    rootTags.put("jaeger.hostname", hostname);
    rootTags.put("sampler.type", "const");
    rootTags.put("sampler.param", true);

    Map<String, Object> childTags = new HashMap<>();
    childTags.put("jaeger.version", SENTINEL);
    childTags.put("jaeger.hostname", SENTINEL);
    childTags.put("sampler.type", SENTINEL);
    childTags.put("sampler.param", SENTINEL);

    Map<String, Object> rpcTags = new HashMap<>();
    rpcTags.put("jaeger.version", Tracer.VERSION);
    rpcTags.put("jaeger.hostname", hostname);
    rpcTags.put("sampler.type", SENTINEL);
    rpcTags.put("sampler.param", SENTINEL);

    List<Object[]> data = new ArrayList<>();
    data.add(new Object[] {SpanType.ROOT, rootTags});
    data.add(new Object[] {SpanType.CHILD, childTags});
    data.add(new Object[] {SpanType.RPC_SERVER, rpcTags});
    return data;
  }

  @Before
  public void setUp() throws Exception {}

  @Test
  public void testTracerTags() throws Exception {
    InMemoryReporter spanReporter = new InMemoryReporter();
    Tracer tracer = new Tracer.Builder("x", spanReporter, new ConstSampler(true)).build();

    Span span = (Span) tracer.buildSpan("root").start();
    if (spanType == SpanType.CHILD) {
      span = (Span) tracer.buildSpan("child").asChildOf(span).start();
    }
    if (spanType == SpanType.RPC_SERVER) {
      span =
          (Span)
              tracer
                  .buildSpan("rpc-server")
                  .asChildOf(span)
                  .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                  .start();
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
