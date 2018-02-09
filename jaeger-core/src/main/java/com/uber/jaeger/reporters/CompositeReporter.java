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

package com.uber.jaeger.reporters;

import com.uber.jaeger.Span;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.ToString;

@ToString
public class CompositeReporter implements Reporter {
  private final List<Reporter> reporters;

  public CompositeReporter(Reporter... reporters) {
    this.reporters = new ArrayList<Reporter>();
    for (int i = 0; i < reporters.length; i++) {
      this.reporters.add(reporters[i]);
    }
  }

  @Override
  public void report(Span span) {
    for (Reporter reporter : this.reporters) {
      reporter.report(span);
    }
  }

  @Override
  public void close() {
    for (Reporter reporter : this.reporters) {
      reporter.close();
    }
  }

  /**
   * The reporters being used
   * @return the list of reporters backing this composite reporter
   */
  public List<Reporter> getReporters() {
    return Collections.unmodifiableList(reporters);
  }
}
