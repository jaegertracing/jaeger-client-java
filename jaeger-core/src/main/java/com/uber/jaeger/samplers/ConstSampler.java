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

public class ConstSampler implements Sampler {
    private final boolean decision;

    public ConstSampler(boolean decision) {
        this.decision = decision;
    }

    /**
     * @param id A long that represents the traceid used to make a sampling decision
     *           the command line arguments.
     * @return A boolean that says whether to sample.
     */
    public boolean isSampled(long id) {
        return decision;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof  ConstSampler) {
            return this.decision == ((ConstSampler) other).decision;
        }
        return false;
    }

    /**
     * Only implemented to satisfy the sampler interface
     */
    public void close() {
        // nothing to do
    }
}