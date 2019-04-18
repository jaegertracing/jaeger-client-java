/*
 * Copyright (c) 2019, The Jaeger Authors
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

import io.opentracing.propagation.Binary;
import java.nio.ByteBuffer;

/**
 * Test implemention of a binary carrier.
 */
public class TestBinaryCarrier implements Binary {

  ByteBuffer buffer = null;

  public TestBinaryCarrier(ByteBuffer b) {
    buffer = b;
  }

  public TestBinaryCarrier() { }

  @Override
  public ByteBuffer injectionBuffer(int length) {
    if (length <= 0) {
      throw new IllegalArgumentException("length must be greater than zero");
    }
    if (buffer == null) {
      buffer = ByteBuffer.allocate(length);
    }
    return buffer;
  }

  @Override
  public ByteBuffer extractionBuffer() {
    return buffer;
  }
}