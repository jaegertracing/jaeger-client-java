package io.jaegertracing.internal;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.util.ThreadLocalScopeManager;

public class MDcScopeManager implements ScopeManager {
  private final ScopeManager wrapped = new ThreadLocalScopeManager();

  @Override
  public Scope activate(Span span) {
    return new MDcScope(wrapped.activate(span), span);
  }

  @Override
  public Span activeSpan() {
    return wrapped.activeSpan();
  }
}
