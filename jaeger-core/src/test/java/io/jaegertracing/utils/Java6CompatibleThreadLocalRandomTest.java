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

package io.jaegertracing.utils;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class Java6CompatibleThreadLocalRandomTest {

  @After
  @Before
  public void clearState() throws Exception {
    // As test classes are targeted at 1.7 and gradle 3.x can't be run with Java 6,
    // its safe to say that this test won't ever be executed with JDK 6
    Java6CompatibleThreadLocalRandom.threadLocalRandomPresent = true;
  }

  @Test
  public void testThreadLocalRandomPresent() throws Exception {
    assertSame(Java6CompatibleThreadLocalRandom.current(), ThreadLocalRandom.current());
    assertNotNull(Java6CompatibleThreadLocalRandom.current().nextLong());
  }

  @Test
  public void testThreadLocalRandomNotPresent() throws Exception {
    Java6CompatibleThreadLocalRandom.threadLocalRandomPresent = false;
    assertNotSame(Java6CompatibleThreadLocalRandom.current(), ThreadLocalRandom.current());
    assertNotNull(Java6CompatibleThreadLocalRandom.current().nextLong());
  }

  @Test
  public void testRandomFromOtherThreadIsNotSame() throws Exception {
    Java6CompatibleThreadLocalRandom.threadLocalRandomPresent = false;
    final AtomicReference<Random> randomFromOtherThread = new AtomicReference<>();
    final Thread thread = new Thread(new Runnable() {
        @Override
        public void run() {
            randomFromOtherThread.set(Java6CompatibleThreadLocalRandom.current());
        }
    });
    thread.start();
    thread.join();
    assertNotSame(Java6CompatibleThreadLocalRandom.current(), randomFromOtherThread.get());
    assertNotNull(Java6CompatibleThreadLocalRandom.current().nextLong());
    assertNotNull(randomFromOtherThread.get());
    assertNotNull(randomFromOtherThread.get().nextLong());
  }

}
