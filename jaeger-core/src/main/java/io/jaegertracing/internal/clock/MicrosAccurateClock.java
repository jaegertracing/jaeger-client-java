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

import java.lang.reflect.Method;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.val;


/**
 * An internal {@link Clock} implementation for JDK/JRE versions 9 and above.
 * pre-generates and stores all reflective accessors on
 * class-loading, minimizing reflective operations in real-time to just 2 non-parameterized method calls.
 * This class is also further optimized by the JIT compiler and Hot-Spot JVM upon recurring use, so we should
 * not suffer any notable performance penalties here.
 *
 * <p>
 *     While it would have been preferable to generate multi-version/release jars to differentiate between the two cases
 *     The project currently targets JDK 1.6, and reworking gradle and release cycle to accommodate for this would be
 *     too much big of change for now.
 * <p>
 * <p>
 *     Thus, the current implementation remains our best option
 *
 * @author <a href="mailto:ishinberg0@gmail.com">Idan Sheinberg</a>
 */

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MicrosAccurateClock implements Clock {

  static final Clock INSTANCE = new MicrosAccurateClock();

  private static final Method NOW;
  private static final Object EPOCH;
  private static final Object CHRONO_UNIT_MICROS;
  private static final Method CHRONO_UNIT_BETWEEN;

  static {
    try {
      val classLoader = ClassLoader.getSystemClassLoader();
      val instant = classLoader.loadClass("java.time.Instant");
      NOW = instant.getMethod("now");
      EPOCH = instant.getField("EPOCH").get(null);

      val chronoUnit = classLoader.loadClass("java.time.temporal.ChronoUnit");
      CHRONO_UNIT_MICROS = chronoUnit.getField("MICROS").get(null);

      val temporal = classLoader.loadClass("java.time.temporal.Temporal");
      CHRONO_UNIT_BETWEEN = chronoUnit.getMethod("between", temporal, temporal);
    } catch (Exception x) {
      throw new IllegalStateException("Could not setup microseconds accurate time supplier", x);
    }
  }

  @Override
  public long currentTimeMicros() {
    try {
      val now = NOW.invoke(null);
      return (Long) CHRONO_UNIT_BETWEEN.invoke(CHRONO_UNIT_MICROS, EPOCH, now);
    } catch (Exception x) {
      throw new IllegalStateException("Could not acquire current microseconds accurate timestamp", x);
    }
  }

  @Override
  public long currentNanoTicks() {
    return System.nanoTime();
  }

  @Override
  public boolean isMicrosAccurate() {
    return true;
  }
}
