/*
 * Copyright (c) 2016-2017, Uber Technologies, Inc
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

package com.uber.jaeger.reporters;

import com.uber.jaeger.Span;
import com.uber.jaeger.exceptions.SenderException;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.senders.Sender;
import com.uber.jaeger.senders.UdpSender;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * RemoteReporter buffers spans in memory and sends them out of process using Sender.
 */
@ToString(exclude = {"commandQueue", "flushTimer", "queueProcessorThread", "metrics"})
@Slf4j
public class RemoteReporter implements Reporter {
  public static final int DEFAULT_FLUSH_INTERVAL_MS = 1000;
  public static final int DEFAULT_MAX_QUEUE_SIZE = 100;

  private static final int DEFAULT_CLOSE_ENQUEUE_TIMEOUT_MILLIS = 1000;
  private final BlockingQueue<Command> commandQueue;
  private final Timer flushTimer;
  private final Thread queueProcessorThread;
  private final QueueProcessor queueProcessor;
  private final Sender sender;
  private final int closeEnqueueTimeout;
  private final Metrics metrics;

  /**
   * RemoteReporter takes a Sender object, and sends spans for a specific protocol, and transport.
   * At this point in time the protocol (thrift) is tightly-coupled to  Reporter, but in the future it may not be.
   *
   * @deprecated use {@link RemoteReporter.Builder} with fluent API
   **/
  @Deprecated
  public RemoteReporter(Sender sender, int flushInterval, int maxQueueSize, Metrics metrics) {
    this(sender, flushInterval, maxQueueSize, DEFAULT_CLOSE_ENQUEUE_TIMEOUT_MILLIS, metrics);
  }

  RemoteReporter(Sender sender, int flushInterval, int maxQueueSize, int closeEnqueueTimeout,
      Metrics metrics) {
    this.sender = sender;
    this.metrics = metrics;
    this.closeEnqueueTimeout = closeEnqueueTimeout;
    commandQueue = new ArrayBlockingQueue<Command>(maxQueueSize);

    // start a thread to append spans
    queueProcessor = new QueueProcessor();
    queueProcessorThread = new Thread(queueProcessor, "jaeger.RemoteReporter-QueueProcessor");
    queueProcessorThread.setDaemon(true);
    queueProcessorThread.start();

    flushTimer = new Timer("jaeger.RemoteReporter-FlushTimer", true /* isDaemon */);
    flushTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            flush();
          }
        },
        flushInterval,
        flushInterval);
  }

  @Override
  public void report(Span span) {
    // Its better to drop spans, than to block here
    boolean added = commandQueue.offer(new AppendCommand(span));

    if (!added) {
      metrics.reporterDropped.inc(1);
    }
  }

  @Override
  public void close() {
    try {
      // best-effort: if we can't add CloseCommand in this time then it probably will never happen
      boolean added = commandQueue
          .offer(new CloseCommand(), closeEnqueueTimeout, TimeUnit.MILLISECONDS);
      if (added) {
        queueProcessorThread.join(10000);
      } else {
        log.warn("Unable to cleanly close RemoteReporter, command queue is full - probably the"
            + " sender is stuck");
      }
    } catch (InterruptedException e) {
      log.error("Interrupted", e);
    } finally {
      try {
        int n = sender.close();
        metrics.reporterSuccess.inc(n);
      } catch (SenderException e) {
        metrics.reporterFailure.inc(e.getDroppedSpanCount());
      }
      flushTimer.cancel();
    }
  }

  void flush() {
    // to reduce the number of updateGauge stats, we only emit queue length on flush
    metrics.reporterQueueLength.update(commandQueue.size());

    // We can safely drop FlushCommand when the queue is full - sender should take care of flushing
    // in such case
    commandQueue.offer(new FlushCommand());
  }

  /*
   * The code below implements the command pattern.  This pattern is useful for
   * situations where multiple threads would need to synchronize on a resource,
   * but are fine with executing sequentially.  The advantage is simplified code where
   * tasks are put onto a blocking queue and processed sequentially by another thread.
   */
  public interface Command {
    void execute() throws SenderException;
  }

  class AppendCommand implements Command {
    private final Span span;

    public AppendCommand(Span span) {
      this.span = span;
    }

    @Override
    public void execute() throws SenderException {
      sender.append(span);
    }
  }

  class CloseCommand implements Command {
    @Override
    public void execute() throws SenderException {
      queueProcessor.close();
    }
  }

  class FlushCommand implements Command {
    @Override
    public void execute() throws SenderException {
      int n = sender.flush();
      metrics.reporterSuccess.inc(n);
    }
  }

  /*
   * This class creates a Runnable that is responsible for appending spans using a sender.
   */
  @ToString
  class QueueProcessor implements Runnable {
    private boolean open = true;

    @Override
    public void run() {
      while (open) {
        try {
          RemoteReporter.Command command = commandQueue.take();

          try {
            command.execute();
          } catch (SenderException e) {
            metrics.reporterFailure.inc(e.getDroppedSpanCount());
          }
        } catch (InterruptedException e) {
          log.error("QueueProcessor error:", e);
          // Do nothing, and try again on next span.
        }
      }
    }

    public void close() {
      open = false;
    }
  }

  public static class Builder {
    private Sender sender;
    private int flushInterval = DEFAULT_FLUSH_INTERVAL_MS;
    private int maxQueueSize = DEFAULT_MAX_QUEUE_SIZE;
    private Metrics metrics;

    public Builder withFlushInterval(int flushInterval) {
      this.flushInterval = flushInterval;
      return this;
    }

    public Builder withMaxQueueSize(int maxQueueSize) {
      this.maxQueueSize = maxQueueSize;
      return this;
    }

    public Builder withMetrics(Metrics metrics) {
      this.metrics = metrics;
      return this;
    }

    public Builder withSender(Sender sender) {
      this.sender = sender;
      return this;
    }

    public RemoteReporter build() {
      if (sender == null) {
        sender = new UdpSender();
      }
      if (metrics == null) {
        metrics = new Metrics(new StatsFactoryImpl(new NullStatsReporter()));
      }
      return new RemoteReporter(sender, flushInterval, maxQueueSize, DEFAULT_CLOSE_ENQUEUE_TIMEOUT_MILLIS, metrics);
    }
  }
}
