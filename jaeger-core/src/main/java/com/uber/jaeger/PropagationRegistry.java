package com.uber.jaeger;

import com.uber.jaeger.propagation.Extractor;
import com.uber.jaeger.propagation.Injector;
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
    return new ExceptionCatchingInjectorDecorator<T>((Injector<T>) injectors.get(format));
  }

  @SuppressWarnings("unchecked")
  <T> Extractor<T> getExtractor(Format<T> format) {
    return new ExceptionCatchingExtractorDecorator<T>((Extractor<T>) extractors.get(format));
  }

  public <T> void register(Format<T> format, Injector<T> injector) {
    injectors.put(format, injector);
  }

  public <T> void register(Format<T> format, Extractor<T> extractor) {
    extractors.put(format, extractor);
  }

  @RequiredArgsConstructor
  @Slf4j
  public static class ExceptionCatchingExtractorDecorator<T> implements Extractor<T> {

    private final Extractor<T> decorated;

    @Override
    public SpanContext extract(T carrier) {
      try {
        return decorated.extract(carrier);
      } catch (RuntimeException ex) {
        ExceptionCatchingExtractorDecorator.log.warn("Error when extracting SpanContext from carrier. Handling gracefully.", ex);
        return null;
      }
    }
  }

  @RequiredArgsConstructor
  @Slf4j
  public static class ExceptionCatchingInjectorDecorator<T> implements Injector<T> {

    private final Injector<T> decorated;

    @Override
    public void inject(SpanContext spanContext, T carrier) {
      try {
        decorated.inject(spanContext, carrier);
      } catch (RuntimeException ex) {
        ExceptionCatchingInjectorDecorator.log.error("Error when injecting SpanContext into carrier. Handling gracefully.", ex);
      }
    }
  }
}
