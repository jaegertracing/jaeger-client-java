/*
 * Copyright (c) 2018, The Jaeger Authors
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

import static org.junit.Assert.assertNull;

import com.uber.jaeger.propagation.Codec;
import com.uber.jaeger.reporters.InMemoryReporter;
import com.uber.jaeger.samplers.ConstSampler;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.Map.Entry;
import org.junit.Before;
import org.junit.Test;

public class TracerResiliencyTest {

  private Tracer tracer;

  @Before
  public void setUpTracer() {
    tracer = new Tracer.Builder("TracerResiliencyTestService")
        .withReporter(new InMemoryReporter())
        .withSampler(new ConstSampler(true))
        .registerExtractor(Builtin.TEXT_MAP, new FaultyCodec())
        .registerInjector(Builtin.TEXT_MAP, new FaultyCodec())
        .build();
  }

  @Test
  public void shouldFallbackWhenExtractingWithFaultyCodec() {
    io.opentracing.SpanContext span = tracer.extract(Builtin.TEXT_MAP, new NoopTextMap());
    assertNull(span);
  }

  @Test
  public void shouldFallbackWhenInjectingWithFaultyCodec() {
    io.opentracing.SpanContext context = tracer.buildSpan("test-span").start().context();
    tracer.inject(context, Builtin.TEXT_MAP, new NoopTextMap());
  }


  private static class FaultyCodec implements Codec<TextMap> {

    @Override
    public SpanContext extract(TextMap carrier) {
      throw new RuntimeException("Some Codecs can be faulty, this one is.");
    }

    @Override
    public void inject(SpanContext spanContext, TextMap carrier) {
      throw new RuntimeException("Some Codecs can be faulty, this one is.");
    }
  }

  private static class NoopTextMap implements TextMap {

    @Override
    public Iterator<Entry<String, String>> iterator() {
      return null;
    }

    @Override
    public void put(String key, String value) {

    }
  }
}
