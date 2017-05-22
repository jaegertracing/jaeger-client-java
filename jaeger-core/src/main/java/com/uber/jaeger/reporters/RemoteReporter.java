/*
 * Copyright (c) 2017, Uber Technologies, Inc
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

package com.uber.jaeger.reporters;

import com.uber.jaeger.Span;
import com.uber.jaeger.exceptions.SenderException;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.senders.Sender;
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
  private static final int DEFAULT_CLOSE_ENQUEUE_TIMEOUT_MILLIS = 1000;
  private final BlockingQueue<Command> commandQueue;
  private final Timer flushTimer;
  private final Thread queueProcessorThread;
  private final QueueProcessor queueProcessor;
  private final Sender sender;
  private final int maxQueueSize;
  private final int closeEnqueueTimeout;
  private final Metrics metrics;

  /*
   * RemoteReporter takes a Sender object, and sends spans for a specific protocol, and transport.
   * At this point in time the protocol (thrift) is tightly-coupled to  Reporter, but in the future it may not be.
   * */
  public RemoteReporter(Sender sender, int flushInterval, int maxQueueSize, Metrics metrics) {
    this(sender, flushInterval, maxQueueSize, DEFAULT_CLOSE_ENQUEUE_TIMEOUT_MILLIS, metrics);
  }

  RemoteReporter(Sender sender, int flushInterval, int maxQueueSize, int closeEnqueueTimeout,
      Metrics metrics) {
    this.sender = sender;
    this.maxQueueSize = maxQueueSize;
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
        queueProcessorThread.join();
      } else {
        log.warn("Unable to cleanly close RemoteReporter, command queue is full - probably the"
            + " sender is stuck");
      }
    } catch (InterruptedException e) {
      return;
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
}
