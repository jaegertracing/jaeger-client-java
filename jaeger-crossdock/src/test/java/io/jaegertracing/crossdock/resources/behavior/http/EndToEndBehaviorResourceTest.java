/*
 * Copyright (c) 2017, Uber Technologies, Inc
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

package io.jaegertracing.crossdock.resources.behavior.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.jaegertracing.JaegerTracer;
import io.jaegertracing.crossdock.api.CreateTracesRequest;
import io.jaegertracing.crossdock.resources.behavior.EndToEndBehavior;
import io.jaegertracing.internal.Span;
import io.jaegertracing.internal.reporters.InMemoryReporter;
import io.jaegertracing.internal.samplers.ConstSampler;
import io.opentracing.Tracer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class EndToEndBehaviorResourceTest {
  private EndToEndBehaviorResource resource;
  private InMemoryReporter reporter;

  @Before
  public void setUp() throws Exception {
    reporter = new InMemoryReporter();
    Tracer tracer =
        new JaegerTracer.Builder("crossdock-java")
            .withReporter(reporter)
            .withSampler(new ConstSampler(true))
            .build();
    Map<String, Tracer> tracers = new HashMap<>();
    tracers.put("const", tracer);
    resource = new EndToEndBehaviorResource(new EndToEndBehavior(tracers));
  }

  @Test
  public void testCreateTraces() throws Exception {
    Map<String, String> tags = new HashMap<String, String>();
    tags.put("key", "value");
    CreateTracesRequest request =
        new CreateTracesRequest("const", "operation", 2, tags);

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
