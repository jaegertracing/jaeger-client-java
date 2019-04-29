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
  private final long maxBalance; // max balance in nano ticks
  private final AtomicLong debit; // last op nano time less remaining balance

  public RateLimiter(double creditsPerSecond, double maxBalance) {
    this(creditsPerSecond, maxBalance, new SystemClock());
  }

  public RateLimiter(double creditsPerSecond, double maxBalance, Clock clock) {
    this.clock = clock;
    this.creditsPerNanosecond = creditsPerSecond / 1.0e9;
    this.maxBalance = (long) (maxBalance / creditsPerNanosecond);
    this.debit = new AtomicLong(clock.currentNanoTicks() - this.maxBalance);
  }

  public boolean checkCredit(double itemCost) {
    long cost = (long) (itemCost / creditsPerNanosecond);
    long credit;
    long currentDebit;
    long balance;
    do {
      currentDebit = debit.get();
      credit = clock.currentNanoTicks();
      balance = credit - currentDebit;
      if (balance > maxBalance) {
        balance = maxBalance;
      }
      balance -= cost;
      if (balance < 0) {
        return false;
      }
    } while (!debit.compareAndSet(currentDebit, credit - balance));
    return true;
  }
}
