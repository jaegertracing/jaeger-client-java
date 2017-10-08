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

public class RateLimiter {
  private final double creditsPerNanosecond;
  private final Clock clock;
  private double balance;
  private double maxBalance;
  private long lastTick;

  public RateLimiter(double creditsPerSecond, double maxBalance) {
    this(creditsPerSecond, maxBalance, new SystemClock());
  }

  public RateLimiter(double creditsPerSecond, double maxBalance, Clock clock) {
    this.clock = clock;
    this.balance = maxBalance;
    this.maxBalance = maxBalance;
    this.creditsPerNanosecond = creditsPerSecond / 1.0e9;
  }

  public boolean checkCredit(double itemCost) {
    long currentTime = clock.currentNanoTicks();
    double elapsedTime = currentTime - lastTick;
    lastTick = currentTime;
    balance += elapsedTime * creditsPerNanosecond;
    if (balance > maxBalance) {
      balance = maxBalance;
    }
    if (balance >= itemCost) {
      balance -= itemCost;
      return true;
    }
    return false;
  }
}
