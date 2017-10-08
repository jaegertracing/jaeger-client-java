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
package com.uber.jaeger.utils;

/**
 * Default implementation of a clock that delegates its calls to the system clock. The
 * microsecond-precision time is simulated by (millis * 1000), therefore the
 * {@link #isMicrosAccurate()} is false.
 *
 * @see System#currentTimeMillis()
 * @see System#nanoTime()
 */
public class SystemClock implements Clock {

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
