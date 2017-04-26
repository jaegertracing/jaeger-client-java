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

package com.uber.jaeger.context;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TracedExecutorService implements ExecutorService {
  private final ExecutorService delegate;
  private final TraceContext traceContext;

  public TracedExecutorService(ExecutorService delegate, TraceContext traceContext) {
    this.delegate = delegate;
    this.traceContext = traceContext;
  }

  @Override
  public void shutdown() {
    delegate.shutdown();
  }

  @Override
  public List<java.lang.Runnable> shutdownNow() {
    return delegate.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return delegate.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  @Override
  public <T> Future<T> submit(java.util.concurrent.Callable<T> task) {
    return delegate.submit(new Callable<T>(task, traceContext));
  }

  @Override
  public <T> Future<T> submit(java.lang.Runnable task, T result) {
    return delegate.submit(new Runnable(task, traceContext), result);
  }

  @Override
  public Future<?> submit(java.lang.Runnable task) {
    return delegate.submit(new Runnable(task, traceContext));
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends java.util.concurrent.Callable<T>> tasks)
      throws InterruptedException {
    return delegate.invokeAll(wrapJaegerCallableCollection(tasks));
  }

  @Override
  public <T> List<Future<T>> invokeAll(
      Collection<? extends java.util.concurrent.Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException {
    return delegate.invokeAll(wrapJaegerCallableCollection(tasks), timeout, unit);
  }

  @Override
  public <T> T invokeAny(Collection<? extends java.util.concurrent.Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    return delegate.invokeAny(wrapJaegerCallableCollection(tasks));
  }

  @Override
  public <T> T invokeAny(
      Collection<? extends java.util.concurrent.Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return delegate.invokeAny(wrapJaegerCallableCollection(tasks), timeout, unit);
  }

  @Override
  public void execute(final java.lang.Runnable command) {
    delegate.execute(new Runnable(command, traceContext));
  }

  private <T> Collection<? extends java.util.concurrent.Callable<T>> wrapJaegerCallableCollection(
      Collection<? extends java.util.concurrent.Callable<T>> originalCollection) {
    Collection<java.util.concurrent.Callable<T>> collection =
        new ArrayList<java.util.concurrent.Callable<T>>(originalCollection.size());
    for (java.util.concurrent.Callable<T> c : originalCollection) {
      collection.add(new Callable<T>(c, traceContext));
    }
    return collection;
  }
}
