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

package com.uber.jaeger;

import static org.junit.Assert.assertEquals;

import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import org.junit.Test;

public class TracerTagsTest {

  @Test
  public void testTracerTags() throws Exception {
    InMemoryReporter spanReporter = new InMemoryReporter();
    Tracer tracer = new Tracer.Builder("x", spanReporter, new ConstSampler(true))
        .withZipkinSharedRpcSpan()
        .withTag("tracer.tag.str", "y")
        .build();

    Span span = (Span) tracer.buildSpan("root").startManual();

    // span should only contain sampler tags and no tracer tags
    assertEquals(2, span.getTags().size());
    assertEquals(true, span.getTags().containsKey("sampler.type"));
    assertEquals(true, span.getTags().containsKey("sampler.param"));
    assertEquals(false, span.getTags().containsKey("tracer.tag.str"));
  }
}
