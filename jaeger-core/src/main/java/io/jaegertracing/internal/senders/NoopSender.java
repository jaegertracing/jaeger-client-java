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

package io.jaegertracing.internal.senders;

import io.jaegertracing.internal.JaegerSpan;
import io.jaegertracing.spi.Sender;
import lombok.ToString;

/**
 * A sender that does not send anything, anywhere. Is used only as a fallback on systems where no senders can be
 * selected.
 */
@ToString
public class NoopSender implements Sender {
  @Override
  public int append(JaegerSpan span) {
    return 1;
  }

  @Override
  public int flush() {
    return 0;
  }

  @Override
  public int close() {
    return 0;
  }
}
