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

package io.jaegertracing.internal.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.jaegertracing.internal.clock.Clock;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class RateLimiterTest {

  private static class MockClock implements Clock {

    long timeNanos;

    @Override
    public long currentTimeMicros() {
      return 0;
    }

    @Override
    public long currentNanoTicks() {
      return timeNanos;
    }

    @Override
    public boolean isMicrosAccurate() {
      return false;
    }
  }

  @Test
  public void testRateLimiterWholeNumber() {
    MockClock clock = new MockClock();
    RateLimiter limiter = new RateLimiter(2.0, 2.0, clock);

    long currentTime = TimeUnit.MICROSECONDS.toNanos(100);
    clock.timeNanos = currentTime;
    assertTrue(limiter.checkCredit(1.0));
    assertTrue(limiter.checkCredit(1.0));
    assertFalse(limiter.checkCredit(1.0));
    // move time 250ms forward, not enough credits to pay for 1.0 item
    currentTime += TimeUnit.MILLISECONDS.toNanos(250);
    clock.timeNanos = currentTime;
    assertFalse(limiter.checkCredit(1.0));

    // move time 500ms forward, now enough credits to pay for 1.0 item
    currentTime += TimeUnit.MILLISECONDS.toNanos(500);
    clock.timeNanos = currentTime;

    assertTrue(limiter.checkCredit(1.0));
    assertFalse(limiter.checkCredit(1.0));

    // move time 5s forward, enough to accumulate credits for 10 messages, but it should still be capped at 2
    currentTime += TimeUnit.MILLISECONDS.toNanos(5000);
    clock.timeNanos = currentTime;

    assertTrue(limiter.checkCredit(1.0));
    assertTrue(limiter.checkCredit(1.0));
    assertFalse(limiter.checkCredit(1.0));
    assertFalse(limiter.checkCredit(1.0));
    assertFalse(limiter.checkCredit(1.0));
  }

  @Test
  public void testRateLimiterLessThanOne() {
    MockClock clock = new MockClock();
    RateLimiter limiter = new RateLimiter(0.5, 0.5, clock);

    long currentTime = TimeUnit.MICROSECONDS.toNanos(100);
    clock.timeNanos = currentTime;
    assertTrue(limiter.checkCredit(0.25));
    assertTrue(limiter.checkCredit(0.25));
    assertFalse(limiter.checkCredit(0.25));
    // move time 250ms forward, not enough credits to pay for 1.0 item
    currentTime += TimeUnit.MILLISECONDS.toNanos(250);
    clock.timeNanos = currentTime;
    assertFalse(limiter.checkCredit(0.25));

    // move time 500ms forward, now enough credits to pay for 1.0 item
    currentTime += TimeUnit.MILLISECONDS.toNanos(500);
    clock.timeNanos = currentTime;

    assertTrue(limiter.checkCredit(0.25));
    assertFalse(limiter.checkCredit(0.25));

    // move time 5s forward, enough to accumulate credits for 10 messages, but it should still be capped at 2
    currentTime += TimeUnit.MILLISECONDS.toNanos(5000);
    clock.timeNanos = currentTime;

    assertTrue(limiter.checkCredit(0.25));
    assertTrue(limiter.checkCredit(0.25));
    assertFalse(limiter.checkCredit(0.25));
    assertFalse(limiter.checkCredit(0.25));
    assertFalse(limiter.checkCredit(0.25));
  }

  @Test
  public void testRateLimiterMaxBalance() {
    MockClock clock = new MockClock();
    RateLimiter limiter = new RateLimiter(0.1, 1.0, clock);

    long currentTime = TimeUnit.MICROSECONDS.toNanos(100);
    clock.timeNanos = currentTime;
    assertTrue(limiter.checkCredit(1.0));
    assertFalse(limiter.checkCredit(1.0));

    // move time 20s forward, enough to accumulate credits for 2 messages, but it should still be capped at 1
    currentTime += TimeUnit.MILLISECONDS.toNanos(20000);
    clock.timeNanos = currentTime;

    assertTrue(limiter.checkCredit(1.0));
    assertFalse(limiter.checkCredit(1.0));
  }

  /**
   * Validates rate limiter behavior with {@link System#nanoTime()}-like (non-zero) initial nano ticks.
   */
  @Test
  public void testRateLimiterInitial() {
    MockClock clock = new MockClock();
    clock.timeNanos = TimeUnit.MILLISECONDS.toNanos(-1_000_000);
    RateLimiter limiter = new RateLimiter(1000, 100, clock);

    assertTrue(limiter.checkCredit(100)); // consume initial (max) balance
    assertFalse(limiter.checkCredit(1));

    clock.timeNanos += TimeUnit.MILLISECONDS.toNanos(49); // add 49 credits
    assertFalse(limiter.checkCredit(50));

    clock.timeNanos += TimeUnit.MILLISECONDS.toNanos(1); // add one credit
    assertTrue(limiter.checkCredit(50)); // consume accrued balance
    assertFalse(limiter.checkCredit(1));

    clock.timeNanos += TimeUnit.MILLISECONDS.toNanos(1_000_000); // add a lot of credits (max out balance)
    assertTrue(limiter.checkCredit(1)); // take one credit

    clock.timeNanos += TimeUnit.MILLISECONDS.toNanos(1_000_000); // add a lot of credits (max out balance)
    assertFalse(limiter.checkCredit(101)); // can't consume more than max balance
    assertTrue(limiter.checkCredit(100)); // consume max balance
    assertFalse(limiter.checkCredit(1));
  }

  /**
   * Validates concurrent credit check correctness.
   */
  @Test
  public void testRateLimiterConcurrency() {
    int numWorkers = ForkJoinPool.getCommonPoolParallelism();
    int creditsPerWorker = 1000;
    MockClock clock = new MockClock();
    RateLimiter limiter = new RateLimiter(1, numWorkers * creditsPerWorker, clock);

    AtomicInteger count = new AtomicInteger();
    for (int w = 0; w < numWorkers; ++w) {
      ForkJoinPool.commonPool().execute(() -> {
        for (int i = 0; i < creditsPerWorker * 2; ++i) {
          if (limiter.checkCredit(1)) {
            count.getAndIncrement(); // count allowed operations
          }
        }
      });
    }
    ForkJoinPool.commonPool().awaitQuiescence(1, TimeUnit.SECONDS);

    assertEquals("Exactly the allocated number of credits must be consumed", numWorkers * creditsPerWorker,count.get());
    assertFalse(limiter.checkCredit(1));
  }

}
