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

package com.uber.jaeger.context;

import io.opentracing.Span;
import java.util.EmptyStackException;
import java.util.Stack;

public class ThreadLocalTraceContext implements TraceContext {
  private final ThreadLocal<Stack<Span>> threadLocal = new SpanStackThreadLocal();

  @Override
  public void push(Span span) {
    if (span == null) {
      throw new NullPointerException();
    }

    Stack<Span> spanStack = threadLocal.get();
    spanStack.push(span);
  }

  @Override
  public boolean isEmpty() {
    return threadLocal.get().isEmpty();
  }

  @Override
  public Span pop() throws EmptyStackException {
    return threadLocal.get().pop();
  }

  @Override
  public Span getCurrentSpan() throws EmptyStackException {
    return threadLocal.get().peek();
  }
  
  private static class SpanStackThreadLocal extends ThreadLocal<Stack<Span>> {

    @Override
    public Stack<Span> initialValue() {
      return new Stack<Span>();
    }
  }
}
