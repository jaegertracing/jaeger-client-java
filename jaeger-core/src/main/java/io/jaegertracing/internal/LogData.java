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

package io.jaegertracing.internal;

import java.util.Map;
import lombok.Getter;

@Getter
public final class LogData {
  private final long time; // time in microseconds
  private final String message;
  private final Map<String, ?> fields;

  LogData(long time, String message, Map<String, ?> fields) {
    this.time = time;
    this.message = message;
    this.fields = fields;
  }
}
