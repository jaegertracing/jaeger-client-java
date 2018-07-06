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

package io.jaegertracing.internal.clock;

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
   * calculate span duration as (end-start). If this method returns false, the {@code JaegerTracer} will
   * use {@link #currentNanoTicks()} for calculating duration instead.
   */
  boolean isMicrosAccurate();
}
