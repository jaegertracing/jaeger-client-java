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

package io.jaegertracing.internal.senders;

import io.jaegertracing.internal.JaegerSpan;
import io.jaegertracing.internal.exceptions.SenderException;
import io.jaegertracing.spi.Sender;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Sender which stores spans in memory. Appending a new span is a blocking operation unless
 * "permitted". By default Integer.MAX_VALUE "appends" are permitted.
 */
public class InMemorySender implements Sender {

  private List<JaegerSpan> appended;
  private List<JaegerSpan> flushed;
  private List<JaegerSpan> received;
  private Semaphore semaphore = new Semaphore(Integer.MAX_VALUE);

  public InMemorySender() {
    appended = new ArrayList<>();
    flushed = new ArrayList<>();
    received = new ArrayList<>();
  }

  public List<JaegerSpan> getAppended() {
    return new ArrayList<>(appended);
  }

  public List<JaegerSpan> getFlushed() {
    return new ArrayList<>(flushed);
  }

  public List<JaegerSpan> getReceived() {
    return new ArrayList<>(received);
  }

  @Override
  public int append(JaegerSpan span) throws SenderException {
    try {
      semaphore.acquire();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    appended.add(span);
    received.add(span);

    return 0;
  }

  @Override
  public int flush() throws SenderException {
    int flushedSpans = appended.size();
    flushed.addAll(appended);
    appended.clear();

    return flushedSpans;
  }

  @Override
  public int close() throws SenderException {
    return flush();
  }

  /**
   * Removes previously granted "append" permits and grants
   * a new number of permits
   *
   * @param number number of "appends" to permit
   */
  public void permitAppend(int number) {
    semaphore.drainPermits();
    semaphore.release(number);
  }
}
