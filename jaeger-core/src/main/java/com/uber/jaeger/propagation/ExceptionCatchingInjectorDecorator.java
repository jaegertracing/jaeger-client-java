package com.uber.jaeger.propagation;

import com.uber.jaeger.SpanContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class ExceptionCatchingInjectorDecorator<T> implements Injector<T> {

  private final Injector<T> decorated;

  @Override
  public void inject(SpanContext spanContext, T carrier) {
    try {
      decorated.inject(spanContext, carrier);
    } catch (RuntimeException ex) {
      log.error("Error when injecting SpanContext into carrier. Handling gracefully.", ex);
    }
  }
}
