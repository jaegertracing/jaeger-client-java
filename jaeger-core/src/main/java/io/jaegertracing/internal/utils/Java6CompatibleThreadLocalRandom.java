/*
 * Copyright (c) 2017, Uber Technologies, Inc
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

package io.jaegertracing.internal.utils;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

public final class Java6CompatibleThreadLocalRandom {

  static boolean threadLocalRandomPresent = true;

  private static final String THREAD_LOCAL_RANDOM_CLASS_NAME =
      "java.util.concurrent.ThreadLocalRandom";

  static {
    try {
      Class.forName(THREAD_LOCAL_RANDOM_CLASS_NAME);
    } catch (ClassNotFoundException e) {
      threadLocalRandomPresent = false;
    }
  }

  private static final ThreadLocal<Random> threadLocal =
      new ThreadLocal<Random>() {
        @Override
        protected Random initialValue() {
          return new Random();
        }
      };

  /**
   * Calls {@link ThreadLocalRandom#current()}, if this class is present (if you are using Java 7).
   * Otherwise uses a Java 6 compatible fallback.
   *
   * @return the current thread's {@link Random}
   */
  public static Random current() {
    if (threadLocalRandomPresent) {
      return ThreadLocalRandomAccessor.getCurrentThreadLocalRandom();
    } else {
      return threadLocal.get();
    }
  }

  /**
   * This class prevents that {@link ThreadLocalRandom} gets loaded unless
   * {@link #getCurrentThreadLocalRandom()} is called
   */
  private static class ThreadLocalRandomAccessor {
    @IgnoreJRERequirement
    private static Random getCurrentThreadLocalRandom() {
      return ThreadLocalRandom.current();
    }
  }

  private Java6CompatibleThreadLocalRandom() {}
}
