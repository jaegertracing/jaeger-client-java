/*
 * Copyright (c) 2021, The Jaeger Authors
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

package io.jaegertracing.internal.reporters;

import io.jaegertracing.internal.JaegerSpan;
import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.internal.metrics.Metrics;
import io.jaegertracing.spi.Reporter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * FilteringReporter reduces the number of spans reported by filtering and deferring them. Spans below the
 * filter threshold are dropped. Spans below the defer threshold are buffered until a parent span exceeds the
 * defer threshold. If a root span is below the defer threshold it and any buffered child spans are dropped.
 */
@ToString
@Slf4j
public class FilteringReporter implements Reporter {
  public static final long DEFAULT_FILTER_SPANS_UNDER_MICROS = 0L;
  public static final long DEFAULT_DEFER_SPANS_UNDER_MICROS = 0L;

  private final Reporter delegate;
  private final long filterSpansUnderMicros;
  private final long deferSpansUnderMicros;
  private final Metrics metrics;

  private final Map<Long, List<JaegerSpan>> pendingByParent = new ConcurrentHashMap<>();
  private final LongAdder pendingCount = new LongAdder();

  public FilteringReporter(
      Reporter delegate, long filterSpansUnderMicros, long deferSpansUnderMicros, Metrics metrics) {
    this.delegate = delegate;
    this.filterSpansUnderMicros = filterSpansUnderMicros;
    this.deferSpansUnderMicros = deferSpansUnderMicros;
    this.metrics = metrics;
  }

  @Override
  public void report(JaegerSpan span) {
    final JaegerSpanContext context = span.context();
    final List<JaegerSpan> pendingChildren = pendingByParent.remove(context.getSpanId());

    if (span.getDuration() < filterSpansUnderMicros) {
      metrics.filteredSpans.inc(1);
      return;
    }

    if (span.getDuration() < deferSpansUnderMicros) {
      defer(span, context, pendingChildren);
    } else {
      // report pending if any, then this span
      if (pendingChildren != null) {
        pendingChildren.forEach(delegate::report);
        final int count = pendingChildren.size();
        pendingCount.add(-1 * count);
        metrics.deferredSpansPending.update(pendingCount.longValue());
        metrics.deferredSpansSent.inc(count);
      }
      delegate.report(span);
    }
  }

  private void defer(final JaegerSpan span, final JaegerSpanContext context,
      final List<JaegerSpan> pendingChildren) {
    final long parentId = context.getParentId();
    final boolean hasParent = parentId != 0;
    final boolean hasPendingChildren = pendingChildren != null;
    if (hasParent) {
      // Defer this span along with any already pending children:
      pendingByParent.compute(parentId, (id, spans) ->
        setOrUpdateParentsPendingSpans(hasPendingChildren, pendingChildren, spans)).add(span);
      pendingCount.increment();
      metrics.deferredSpansPending.update(pendingCount.longValue());
      metrics.deferredSpansStarted.inc(1);
    } else if (hasPendingChildren) {
      // The current span is a top-level span that does not meet the criteria so all the previously pending
      // children need to be marked as dropped:
      final int count = pendingChildren.size();
      pendingCount.add(-1 * count);
      metrics.deferredSpansPending.update(pendingCount.longValue());
      metrics.deferredSpansDropped.inc(count);
    }
  }

  /**
   * This is the remapping function used to update the pendingByParent map. This method returns the list to be
   * used for all spans pending on a parent span. This method adds any spans pending on the current span to
   * this list, and then the caller will add the current span.
   */
  static List<JaegerSpan> setOrUpdateParentsPendingSpans(final boolean hasPendingChildren,
      final List<JaegerSpan> pendingOnThisSpan,
      final List<JaegerSpan> pendingOnParentSpan) {  // visible for testing
    if (pendingOnParentSpan == null) {
      // First sibling to be deferred, either promote the existing pending children list or create a new list:
      return hasPendingChildren ? pendingOnThisSpan : new ArrayList<>(1);
    }
    // A list has already been created by a sibling. Copy over this span's pending children if any:
    if (hasPendingChildren) {
      pendingOnParentSpan.addAll(pendingOnThisSpan);
    }
    return pendingOnParentSpan;
  }

  @Override
  public void close() {
    pendingByParent.values().forEach(pendingSpans -> metrics.deferredSpansDropped.inc(pendingSpans.size()));
    pendingByParent.clear();
    delegate.close();
    pendingCount.reset();
    metrics.deferredSpansPending.update(0);
  }
}
