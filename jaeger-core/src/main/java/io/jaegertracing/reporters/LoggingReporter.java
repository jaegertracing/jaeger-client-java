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

package io.jaegertracing.reporters;

import io.jaegertracing.Span;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LoggingReporter logs every span it's given.
 */
@ToString
public class LoggingReporter implements Reporter {
  private final Logger logger;

  public LoggingReporter() {
    this(null);
  }

  public LoggingReporter(Logger logger) {
    if (logger == null) {
      logger = LoggerFactory.getLogger(this.getClass());
    }
    this.logger = logger;
  }

  @Override
  public void report(Span span) {
    logger.info("Span reported: {}", span);
  }

  @Override
  public void close() {
    // nothing to do
  }
}
