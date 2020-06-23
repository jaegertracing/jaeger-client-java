package io.jaegertracing.internal;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.util.ThreadLocalScopeManager;

public class MDCScopeManager implements ScopeManager {
    private final ScopeManager wrapped = new ThreadLocalScopeManager();

    @Override
    public Scope activate(Span span) {
        return new MDCScope(wrapped.activate(span), span);
    }

    @Override
    public Span activeSpan() {
        return wrapped.activeSpan();
    }
}
