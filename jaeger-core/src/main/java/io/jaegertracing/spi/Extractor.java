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

package io.jaegertracing.spi;

import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.internal.propagation.B3TextMapCodec;
import io.jaegertracing.internal.propagation.TextMapCodec;

/**
 * <p>You should implement this class if you want to add possibility to extract information about
 * JaegerSpanContext that is provided in your custom propagation scheme. Otherwise you should probably use
 * built-in {@link TextMapCodec} or {@link B3TextMapCodec}</p>
 *
 * @see B3TextMapCodec
 * @see TextMapCodec
 * @see Codec
 */
public interface Extractor<T> {

  /**
   * <p>Called when {@link io.opentracing.Tracer#extract(io.opentracing.propagation.Format, Object)}
   * is used. It should handle the logic behind extracting propagation-scheme
   * specific information from carrier (e.g. http request headers, amqp message headers, etc.).</p>
   *
   * <p>This method must not modify the carrier</p>
   *
   * <p>All exceptions thrown from this method will be caught and logged on {@code WARN} level so
   * that business code execution isn't affected. If possible, catch implementation specific
   * exceptions and log more meaningful information.</p>
   *
   * @param carrier input that you extract Span information from, usually {@link
   * io.opentracing.propagation.TextMap}.
   * @return {@link JaegerSpanContext} or {@code null} if carrier doesn't contain tracing information, it
   * is not valid or is incomplete
   * @see B3TextMapCodec
   * @see TextMapCodec
   */
  JaegerSpanContext extract(T carrier);
}
