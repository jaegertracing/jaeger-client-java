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

import com.uber.jaeger.exceptions.SamplingStrategyErrorException;
import com.uber.jaeger.exceptions.UnknownSamplingStrategyException;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.thrift.sampling_manager.ProbabilisticSamplingStrategy;
import com.uber.jaeger.thrift.sampling_manager.RateLimitingSamplingStrategy;
import com.uber.jaeger.thrift.sampling_manager.SamplingStrategyResponse;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RemoteControlledSampler implements Sampler {
    private final String serviceName;
    private final SamplingManager manager;
    private final Timer pollTimer;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Sampler sampler;
    private Metrics metrics;

    public RemoteControlledSampler(String serviceName, SamplingManager manager, Sampler initial, Metrics metrics) {
        final int pollingIntervalMs = 60000;
        this.serviceName = serviceName;
        this.manager = manager;
        this.metrics = metrics;


        if (initial != null) {
            this.sampler = initial;
        } else {
            this.sampler = new ProbabilisticSampler(0.001);
        }

        pollTimer = new Timer(true); // true makes this a daemon thread
        pollTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateSampler();
            }
        }, 0, pollingIntervalMs);
    }

    public ReentrantReadWriteLock getLock() {
        return lock;
    }

    private void updateSampler() {
        SamplingStrategyResponse response;
        try {
            response = manager.getSamplingStrategy(serviceName);
            metrics.samplerRetrieved.inc(1);
        } catch (SamplingStrategyErrorException e) {
            metrics.samplerQueryFailure.inc(1);

            return;
        }

        Sampler newSampler;
        try {
            newSampler = extractSampler(response);
        } catch (UnknownSamplingStrategyException e) {
            metrics.samplerParsingFailure.inc(1);
            return;  // sampler updateGauge without a new sampler
        }

        if (!this.sampler.equals(newSampler)) {
            synchronized (this) {
                this.sampler = newSampler;
                metrics.samplerUpdated.inc(1);

            }
        }
    }

    private Sampler extractSampler(SamplingStrategyResponse response) throws UnknownSamplingStrategyException {
        if (response.isSetProbabilisticSampling()) {
            ProbabilisticSamplingStrategy strategy = response.getProbabilisticSampling();
            return new ProbabilisticSampler(strategy.getSamplingRate());
        }

        if (response.isSetRateLimitingSampling()) {
            RateLimitingSamplingStrategy strategy = response.getRateLimitingSampling();
            return new RateLimitingSampler(strategy.getMaxTracesPerSecond());
        }

        throw new UnknownSamplingStrategyException(String.format("Unsupported sampling strategy type %s", response.getStrategyType()));
    }


    @Override
    public boolean isSampled(long id) {
        synchronized (this) {
            return sampler.isSampled(id);
        }
    }

    @Override
    public boolean equals(Object sampler) {
        if (sampler instanceof RemoteControlledSampler) {
            RemoteControlledSampler remoteSampler = ((RemoteControlledSampler) sampler);
            synchronized (this) {
                ReentrantReadWriteLock.ReadLock readLock = remoteSampler.getLock().readLock();
                readLock.lock();
                try {
                    return this.sampler.equals(remoteSampler.sampler);
                } finally {
                    readLock.unlock();
                }
            }
        }
        return false;
    }

    @Override
    public void close() {
        synchronized (this) {
            pollTimer.cancel();
        }

    }
}