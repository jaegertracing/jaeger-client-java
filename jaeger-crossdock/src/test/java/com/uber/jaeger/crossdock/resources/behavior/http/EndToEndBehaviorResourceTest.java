/*
 * Copyright (c) 2017, Uber Technologies, Inc
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
package com.uber.jaeger.crossdock.resources.behavior.http;

import static org.junit.Assert.*;

import com.uber.jaeger.Span;
import com.uber.jaeger.crossdock.api.*;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import io.opentracing.Tracer;
import java.util.*;
import org.junit.Before;
import org.junit.Test;

public class EndToEndBehaviorResourceTest {
  private EndToEndBehaviorResource resource;
  private InMemoryReporter reporter;

  @Before
  public void setUp() throws Exception {
    reporter = new InMemoryReporter();
    Tracer tracer =
        new com.uber.jaeger.Tracer.Builder("crossdock-java", reporter, new ConstSampler(true))
            .build();
    Map<String, Tracer> tracers = new HashMap<String, Tracer>();
    tracers.put("const", tracer);
    resource = new EndToEndBehaviorResource(tracers);
  }

  @Test
  public void testConstructor() throws Exception {
    EndToEndBehaviorResource testResource = new EndToEndBehaviorResource("localhost");
    assertNotNull(testResource);
  }

  @Test
  public void testCreateTraces() throws Exception {
    Map<String, String> tags = new HashMap<String, String>();
    tags.put("key", "value");
    CreateTracesRequest request = new CreateTracesRequest("const", "operation", 2, tags);

    resource.createTraces(request);
    validateSpans(reporter.getSpans(), request);
  }

  private void validateSpans(List<Span> spans, CreateTracesRequest request) {
    assertEquals(request.getCount(), spans.size());
    for (Span s : spans) {
      assertEquals(request.getOperation(), s.getOperationName());
      Map<String, Object> tags = s.getTags();
      for (Map.Entry<String, String> entry : request.getTags().entrySet()) {
        assertTrue(tags.containsKey(entry.getKey()));
        Object value = tags.get(entry.getKey());
        assertEquals(entry.getValue(), value);
      }
    }
  }
}
