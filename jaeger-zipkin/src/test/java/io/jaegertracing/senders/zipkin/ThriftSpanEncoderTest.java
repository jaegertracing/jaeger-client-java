/*
 * Copyright (c) 2018, The Jaeger Authors
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

package io.jaegertracing.senders.zipkin;

import java.util.Collections;
import org.junit.Test;

// This file is here to satisfy unit test coverage of unsupported methods
public class ThriftSpanEncoderTest {

  ThriftSpanEncoder encoder = new ThriftSpanEncoder();

  /** sizeInBytes isn't used, but the fact we don't support it kicks test coverage */
  @Test(expected = UnsupportedOperationException.class)
  public void sizeInBytes_unsupported() {
    encoder.sizeInBytes(new com.twitter.zipkin.thriftjava.Span());
  }

  /** encodeList isn't used, but the fact we don't support it kicks test coverage */
  @Test(expected = UnsupportedOperationException.class)
  public void encodeList_unsupported() {
    encoder.encodeList(Collections.emptyList());
  }
}
