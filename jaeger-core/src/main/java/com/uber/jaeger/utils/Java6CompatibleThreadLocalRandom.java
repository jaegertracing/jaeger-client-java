/*
 * Copyright (c) 2017, Uber Technologies, Inc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.uber.jaeger.utils;

import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

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
