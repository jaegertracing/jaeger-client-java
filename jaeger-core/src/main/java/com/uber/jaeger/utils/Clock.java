/*
 * Copyright (c) 2016, Uber Technologies, Inc
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

/**
 * A small abstraction around system clock that aims to provide microsecond precision with the best
 * accuracy possible.
 */
public interface Clock {
  /**
   * Returns the current time in microseconds.
   *
   * @return the difference, measured in microseconds, between the current time and and the Epoch
   * (that is, midnight, January 1, 1970 UTC).
   */
  long currentTimeMicros();

  /**
   * Returns the current value of the running Java Virtual Machine's high-resolution time source, in
   * nanoseconds.
   *
   * <p>
   * This method can only be used to measure elapsed time and is not related to any other notion of
   * system or wall-clock time.
   *
   * @return the current value of the running Java Virtual Machine's high-resolution time source, in
   * nanoseconds
   */
  long currentNanoTicks();

  /**
   * @return true if the time returned by {@link #currentTimeMicros()} is accurate enough to
   * calculate span duration as (end-start). If this method returns false, the {@code Tracer} will
   * use {@link #currentNanoTicks()} for calculating duration instead.
   */
  boolean isMicrosAccurate();
}
