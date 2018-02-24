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

package com.uber.jaeger.context;

import io.opentracing.Tracer;
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
  private final Tracer tracer;

  public TracedExecutorService(ExecutorService delegate, Tracer tracer) {
    this.delegate = delegate;
    this.tracer = tracer;
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
    return delegate.submit(new Callable<T>(task, tracer));
  }

  @Override
  public <T> Future<T> submit(java.lang.Runnable task, T result) {
    return delegate.submit(new Runnable(task, tracer), result);
  }

  @Override
  public Future<?> submit(java.lang.Runnable task) {
    return delegate.submit(new Runnable(task, tracer));
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
    delegate.execute(new Runnable(command, tracer));
  }

  private <T> Collection<? extends java.util.concurrent.Callable<T>> wrapJaegerCallableCollection(
      Collection<? extends java.util.concurrent.Callable<T>> originalCollection) {
    Collection<java.util.concurrent.Callable<T>> collection =
        new ArrayList<java.util.concurrent.Callable<T>>(originalCollection.size());
    for (java.util.concurrent.Callable<T> c : originalCollection) {
      collection.add(new Callable<T>(c, tracer));
    }
    return collection;
  }
}
