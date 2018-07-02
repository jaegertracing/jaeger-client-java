/*
 * Copyright (c) 2017, Uber Technologies, Inc
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

package io.jaegertracing.spi;

import io.jaegertracing.internal.baggage.http.BaggageRestrictionResponse;
import io.jaegertracing.internal.exceptions.BaggageRestrictionManagerException;
import java.util.List;

/**
 * BaggageRestrictionManagerProxy is an interface for a class that fetches baggage
 * restrictions for specific service from a remote source i.e. jaeger-agent.
 */
public interface BaggageRestrictionManagerProxy {
  List<BaggageRestrictionResponse> getBaggageRestrictions(String serviceName)
      throws BaggageRestrictionManagerException;
}
