package com.uber.jaeger.propagation;

import com.uber.jaeger.SpanContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class ExceptionCatchingExtractorDecorator<T> implements Extractor<T> {

  private final Extractor<T> decorated;

  @Override
  public SpanContext extract(T carrier) {
    try {
      return decorated.extract(carrier);
    } catch (RuntimeException ex) {
      log.warn("Error when extracting SpanContext from carrier. Handling gracefully.", ex);
      return null;
    }
  }
}
