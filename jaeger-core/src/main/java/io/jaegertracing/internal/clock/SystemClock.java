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

import lombok.val;

/**
 * This implementation of the system-clock will provide true microseconds accurate timestamp,
 * given that the JVM supports it (JDK 9 and above).
 * <p>
 * The actual timestamp generation implementation for both scenarios can be
 * found in {@link MicrosAccurateClock} and {@link MillisAccurrateClock}.
 *
 * @author <a href="mailto:ishinberg0@gmail.com">Idan Sheinberg</a>
 */
public class SystemClock implements Clock {

  private static final Clock DELEGATE;

  private static int getJavaVersion() {
    return parseJavaVersion(System.getProperty("java.version"));
  }
  
  static int parseJavaVersion(String property) {
    val sections = property.split("\\.");
    // Checking if major is in fact GA release or not (see please https://openjdk.java.net/jeps/223)
    val index = sections[0].indexOf("-");
    val major = Integer.parseInt((index == -1) ? sections[0] : sections[0].substring(0, index));
    return major == 1 ? Integer.parseInt(sections[1]) : major;
  }

  static {
    val version = getJavaVersion();
    DELEGATE = version >= 9
        ? MicrosAccurateClock.INSTANCE
        : MillisAccurrateClock.INSTANCE;
  }

  @Override
  public long currentTimeMicros() {
    return DELEGATE.currentTimeMicros();
  }

  @Override
  public long currentNanoTicks() {
    return DELEGATE.currentNanoTicks();
  }

  @Override
  public boolean isMicrosAccurate() {
    return DELEGATE.isMicrosAccurate();
  }
}
