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

package io.jaegertracing.internal;

import io.jaegertracing.spi.Extractor;
import io.jaegertracing.spi.Injector;
import io.opentracing.propagation.Format;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

class PropagationRegistry {

  private final Map<Format<?>, Injector<?>> injectors = new HashMap<Format<?>, Injector<?>>();
  private final Map<Format<?>, Extractor<?>> extractors = new HashMap<Format<?>, Extractor<?>>();

  @SuppressWarnings("unchecked")
  <T> Injector<T> getInjector(Format<T> format) {
    return (Injector<T>) injectors.get(format);
  }

  @SuppressWarnings("unchecked")
  <T> Extractor<T> getExtractor(Format<T> format) {
    return (Extractor<T>) extractors.get(format);
  }

  public <T> void register(Format<T> format, Injector<T> injector) {
    injectors.put(format, new ExceptionCatchingInjectorDecorator<T>(injector));
  }

  public <T> void register(Format<T> format, Extractor<T> extractor) {
    extractors.put(format, new ExceptionCatchingExtractorDecorator<T>(extractor));
  }

  @RequiredArgsConstructor
  @Slf4j
  private static class ExceptionCatchingExtractorDecorator<T> implements Extractor<T> {

    private final Extractor<T> decorated;

    @Override
    public JaegerSpanContext extract(T carrier) {
      try {
        return decorated.extract(carrier);
      } catch (RuntimeException ex) {
        ExceptionCatchingExtractorDecorator.log
            .warn("Error when extracting SpanContext from carrier. Handling gracefully.", ex);
        return null;
      }
    }
  }

  @RequiredArgsConstructor
  @Slf4j
  private static class ExceptionCatchingInjectorDecorator<T> implements Injector<T> {

    private final Injector<T> decorated;

    @Override
    public void inject(JaegerSpanContext spanContext, T carrier) {
      try {
        decorated.inject(spanContext, carrier);
      } catch (RuntimeException ex) {
        ExceptionCatchingInjectorDecorator.log
            .error("Error when injecting SpanContext into carrier. Handling gracefully.", ex);
      }
    }
  }
}
