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
package com.uber.jaeger.reporters;

import com.uber.jaeger.Span;
import com.uber.jaeger.exceptions.SenderException;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.reporters.protocols.ThriftSpanConverter;
import com.uber.jaeger.senders.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * RemoteReporter buffers spans in memory and sends them out of process using Sender.
 */
public class RemoteReporter implements Reporter {
  private final BlockingQueue<Command> commandQueue;
  private final Timer flushTimer;
  private final Thread queueProcessorThread;
  private final QueueProcessor queueProcessor;
  private final Sender sender;
  private final int maxQueueSize;
  private final Metrics metrics;
  private final static Logger logger = LoggerFactory.getLogger(RemoteReporter.class);

  /*
   * RemoteReporter takes a Sender object, and sends spans for a specific protocol, and transport.
   * At this point in time the protocol (thrift) is tightly-coupled to  Reporter, but in the future it may not be.
   * */
  public RemoteReporter(final Sender sender, int flushInterval, int maxQueueSize, Metrics metrics) {
    this.sender = sender;
    this.maxQueueSize = maxQueueSize;
    this.metrics = metrics;
    commandQueue = new ArrayBlockingQueue<>(maxQueueSize);

    // start a thread to append spans
    queueProcessor = new QueueProcessor();
    queueProcessorThread = new Thread(queueProcessor);
    queueProcessorThread.setDaemon(true);
    queueProcessorThread.start();

    flushTimer = new Timer();
    flushTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            commandQueue.add(new FlushCommand());
          }
        },
        0,
        flushInterval);
  }

  @Override
  public void report(Span span) {
    // Its better to drop spans, than to block here
    if (commandQueue.size() == maxQueueSize) {
      metrics.reporterDropped.inc(1);
      return;
    }

    commandQueue.add(new AppendCommand(ThriftSpanConverter.convertSpan(span)));
  }

  @Override
  public void close() {
    commandQueue.add(new CloseCommand());

    try {
      queueProcessorThread.join();
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
    private final com.twitter.zipkin.thriftjava.Span span;

    public AppendCommand(com.twitter.zipkin.thriftjava.Span span) {
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
      // to reduce the number of updateGauge stats, we only emit queue length on flush
      metrics.reporterQueueLength.update(commandQueue.size());
    }
  }

  /*
   * This class creates a Runnable that is responsible for appending spans using a sender.
   */
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
          logger.error("QueueProcessor error:", e);
          // Do nothing, and try again on next span.
        }
      }
    }

    public void close() {
      open = false;
    }
  }
}
