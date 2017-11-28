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

import com.uber.jaeger.SpanContext;
import com.uber.jaeger.propagation.Extractor;
import com.uber.jaeger.propagation.HexCodec;
import com.uber.jaeger.propagation.Injector;
import com.uber.jaeger.propagation.TextMapCodec;

import io.opentracing.propagation.TextMap;
import java.util.Map;

/**
 * This format is compatible with other trace libraries such as Brave, Wingtips, zipkin-js, etc.
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
public final class B3TextMapCodec extends TextMapCodec {

  static final String TRACE_ID_NAME = TextMapCodec.TRACE_ID_NAME;
  static final String SPAN_ID_NAME = TextMapCodec.SPAN_ID_NAME;

  public B3TextMapCodec() {
    super(true);
  }

  @Override
  public void inject(SpanContext spanContext, TextMap carrier) {
    super.injectB3(spanContext, carrier);
  }

  @Override
  public SpanContext extract(TextMap carrier) {
    return super.extractB3(carrier);
  }
}
