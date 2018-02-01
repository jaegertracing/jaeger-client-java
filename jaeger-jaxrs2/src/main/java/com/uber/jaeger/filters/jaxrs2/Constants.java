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

package com.uber.jaeger.filters.jaxrs2;

import javax.ws.rs.Priorities;

public interface Constants {
  /* Jersey context property tag for current active span */
  String CURRENT_SPAN_CONTEXT_KEY = "io.opentracing.Span";

  /* Jersey Filter Priorites */
  int CLIENT_SPAN_CREATION_FILTER_PRIORITY = 1;
  int CLIENT_SPAN_INJECTION_FILTER_PRIORITY = Priorities.USER + 2000;

  int SERVER_SPAN_CREATION_FILTER_PRIORITY = 1;
}
