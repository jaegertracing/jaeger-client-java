/*
 * Copyright (c) 2017, The Jaeger Authors
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

package io.jaegertracing.internal.propagation;

import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.spi.Codec;
import java.util.LinkedList;
import java.util.List;

public class CompositeCodec<T> implements Codec<T> {

  private final java.util.List<Codec<T>> codecs;

  public CompositeCodec(List<Codec<T>> codecs) {
    this.codecs = new LinkedList<Codec<T>>(codecs);
  }

  @Override
  public void inject(JaegerSpanContext spanContext, T carrier) {
    for (Codec<T> codec : codecs) {
      codec.inject(spanContext, carrier);
    }
  }

  @Override
  public JaegerSpanContext extract(T carrier) {
    for (Codec<T> codec : codecs) {
      JaegerSpanContext context = codec.extract(carrier);
      if (context != null) {
        return context;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    for (Codec<T> codec : codecs) {
      if (buffer.length() > 0) {
        buffer.append(" : ");
      }
      buffer.append(codec.toString());
    }
    return buffer.toString();
  }

}
