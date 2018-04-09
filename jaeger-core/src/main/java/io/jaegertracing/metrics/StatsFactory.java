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

package io.jaegertracing.metrics;

/**
 * @deprecated Use {@link MetricsFactory} instead. At the moment of the deprecation, all methods from this interface
 * were moved to the new interface and this interface was made to extend the new one. This allows current
 * implementations of this interface to seamlessly move to the new interface.
 */
@Deprecated
public interface StatsFactory extends MetricsFactory {
}
