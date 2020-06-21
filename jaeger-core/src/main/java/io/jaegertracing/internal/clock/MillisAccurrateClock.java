/*
 * Copyright (c) 2020, The Jaeger Authors
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

package io.jaegertracing.internal.clock;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * An internal {@link Clock} implementation for JDK/JRE versions 8 and below.
 * Calls {@link System#currentTimeMillis} and multiplies the result by 1000
 *
 * @author <a href="mailto:ishinberg0@gmail.com">Idan Sheinberg</a>
 * @see System#currentTimeMillis()
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MillisAccurrateClock implements Clock {

  static final Clock INSTANCE = new MillisAccurrateClock();

  @Override
  public long currentTimeMicros() {
    return System.currentTimeMillis() * 1000;
  }

  @Override
  public long currentNanoTicks() {
    return System.nanoTime();
  }

  @Override
  public boolean isMicrosAccurate() {
    return false;
  }
}
