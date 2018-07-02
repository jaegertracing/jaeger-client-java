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

package io.jaegertracing.internal.reporters;

import io.jaegertracing.internal.JaegerSpan;
import io.jaegertracing.spi.Reporter;
import java.util.ArrayList;
import java.util.List;
import lombok.ToString;

@ToString
public class InMemoryReporter implements Reporter {
  private final List<JaegerSpan> spans;

  public InMemoryReporter() {
    this.spans = new ArrayList<JaegerSpan>();
  }

  @Override
  public void report(JaegerSpan span) {
    synchronized (this) {
      spans.add(span);
    }
  }

  @Override
  public void close() {}

  public List<JaegerSpan> getSpans() {
    synchronized (this) {
      return spans;
    }
  }

  public void clear() {
    synchronized (this) {
      spans.clear();
    }
  }
}
