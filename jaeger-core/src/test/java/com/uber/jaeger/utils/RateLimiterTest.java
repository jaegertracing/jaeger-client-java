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

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class RateLimiterTest {
  RateLimiter limiter;

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
}
