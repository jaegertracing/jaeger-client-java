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
package com.uber.jaeger.context;

import io.opentracing.Span;

/**
 * TraceContext allows an application access and manipulation of the current span state.
 **/
// TODO(oibe) move to opentracing/utils
public interface TraceContext {

  /**
   * Adds the given span to the TraceContext
   *
   * @param span The span to be pushed onto the thread local stacked.
   * */
  void push(Span span);

  /**
   * Removes a span from the TraceContext
   *
   * @return returns and removes the current span from the top of the stack
   * */
  Span pop();

  /**
   * Retrieves the current span without modifying the TraceContext
   *
   * @return returns the current span on the thread local stack without removing it from the stack.
   * */
  Span getCurrentSpan();

  /**
   * Checks if their is any span set in the current TraceContext
   *
   * @return returns a boolean saying wheter or not the thread local is empty
   * */
  boolean isEmpty();
}
