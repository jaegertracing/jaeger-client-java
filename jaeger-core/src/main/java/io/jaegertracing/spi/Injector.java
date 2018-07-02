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
import io.opentracing.propagation.Format;

/**
 * <p>You should implement this class if you want to add possibility to inject information about
 * JaegerSpanContext that is passed between services in your custom propagation scheme. Otherwise you
 * should probably use built-in {@link TextMapCodec} or {@link B3TextMapCodec}</p>
 *
 * @see TextMapCodec
 * @see B3TextMapCodec
 * @see Codec
 */
public interface Injector<T> {

  /**
   * <p>Called when {@link io.opentracing.Tracer#inject(io.opentracing.SpanContext, Format, Object)}
   * is used. It should handle the logic behind injecting propagation scheme
   * specific information into the carrier (e.g. http request headers, amqp message headers,
   * etc.).</p>
   *
   * <p>All exceptions thrown from this method will be caught and logged on {@code ERROR} level so
   * that business code execution isn't affected. If possible, catch implementation specific
   * exceptions and log more meaningful information.</p>
   *
   * @param spanContext span context that should be used to pass trace information with the carrier
   * @param carrier holder of data that is used to pass tracing information between processes
   * @see B3TextMapCodec
   * @see TextMapCodec
   */
  void inject(JaegerSpanContext spanContext, T carrier);
}
