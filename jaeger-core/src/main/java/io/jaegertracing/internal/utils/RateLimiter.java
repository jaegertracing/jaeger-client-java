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

import io.jaegertracing.internal.clock.Clock;
import io.jaegertracing.internal.clock.SystemClock;

import java.util.concurrent.atomic.AtomicLong;

public class RateLimiter {
  private final Clock clock;
  private final double creditsPerNanosecond;
  private final long maxBalance;
  private final AtomicLong balance; // grows over time at creditsPerNanosecond rate up to maxBalance
  private final AtomicLong lastTick;

  public RateLimiter(double creditsPerSecond, double maxBalance) {
    this(creditsPerSecond, maxBalance, new SystemClock());
  }

  public RateLimiter(double creditsPerSecond, double maxBalance, Clock clock) {
    this.clock = clock;
    this.creditsPerNanosecond = creditsPerSecond / 1.0e9;
    this.maxBalance = (long) (maxBalance / creditsPerNanosecond);
    this.balance = new AtomicLong(this.maxBalance);
    this.lastTick = new AtomicLong(clock.currentNanoTicks());
  }

  public boolean checkCredit(double itemCost) {
    long currentTime = clock.currentNanoTicks();
    long accruedNanos = currentTime - lastTick.getAndSet(currentTime); // may be negative
    long nanoCost = (long) (itemCost / creditsPerNanosecond);
    return updateBalance(accruedNanos, nanoCost);
  }

  /**
   * Update the balance with accrued credits (up to max balance) and operation cost (if updated balance is sufficient).
   *
   * @param accrued accrued nanos since last check
   * @param cost operation cost (in nanos)
   *
   * @return {@code true} iff there was sufficient balance (given the operation cost)
   */
  private boolean updateBalance(long accrued, long cost) {
    long currentBalance;
    long newBalance;
    boolean enoughBalance;
    do {
      currentBalance = balance.get();
      newBalance = currentBalance + accrued;
      if (newBalance > maxBalance) {
        newBalance = maxBalance;
      }
      enoughBalance = newBalance >= cost;
      if (enoughBalance) {
        newBalance -= cost;
      }
    } while (!balance.compareAndSet(currentBalance, newBalance));
    return enoughBalance;
  }
}
