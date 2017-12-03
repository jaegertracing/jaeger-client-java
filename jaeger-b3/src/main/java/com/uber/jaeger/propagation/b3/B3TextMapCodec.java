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

package com.uber.jaeger.propagation.b3;

/**
 * This format is compatible with other Zipkin based trace libraries such as Brave, Wingtips, zipkin-js, etc.
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * b3Codec = new B3TextMapCodec();
 * tracer = new Tracer.Builder(serviceName, reporter, sampler)
 *                    .registerInjector(Format.Builtin.HTTP_HEADERS, b3Codec)
 *                    .registerExtractor(Format.Builtin.HTTP_HEADERS, b3Codec)
 *                    ...
 * }</pre>
 *
 * <p>
 * See <a href="http://zipkin.io/pages/instrumenting.html">Instrumenting a Library</a>
 */
public final class B3TextMapCodec extends com.uber.jaeger.propagation.B3TextMapCodec {

  static final String TRACE_ID_NAME = com.uber.jaeger.propagation.B3TextMapCodec.TRACE_ID_NAME;
  static final String SPAN_ID_NAME = com.uber.jaeger.propagation.B3TextMapCodec.SPAN_ID_NAME;

}
