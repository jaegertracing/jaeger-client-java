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
