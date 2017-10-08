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
