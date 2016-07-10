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
package com.uber.jaeger.samplers;

import com.uber.jaeger.utils.RateLimiter;

public class RateLimitingSampler implements Sampler {
    private final RateLimiter rateLimiter;
    private final int maxTracesPerSecond;

    public RateLimitingSampler(int maxTracesPerSecond) {
        this.maxTracesPerSecond = maxTracesPerSecond;
        this.rateLimiter = new RateLimiter(maxTracesPerSecond);

    }

    public boolean isSampled(long id) {
        return this.rateLimiter.checkCredit(1.0);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof  RateLimitingSampler) {
            return this.maxTracesPerSecond == ((RateLimitingSampler) other).maxTracesPerSecond;
        }
        return false;
    }

    /**
     * Only implemented to maintain compatibility with sampling interface.
     */
    public void close() {

    }
}