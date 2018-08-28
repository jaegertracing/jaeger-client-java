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

public class Constants {
  // TODO these should be configurable
  public static final String X_UBER_SOURCE = "x-uber-source";

  /**
   * Span tag key to describe the type of sampler used on the root span.
   */
  public static final String SAMPLER_TYPE_TAG_KEY = "sampler.type";

  /**
   * Span tag key to describe the parameter of the sampler used on the root span.
   */
  public static final String SAMPLER_PARAM_TAG_KEY = "sampler.param";

  /**
   * The name of HTTP header or a TextMap carrier key which, if found in the carrier, forces the
   * trace to be sampled as "debug" trace. The value of the header is recorded as the tag on the
   * root span, so that the trace can be found in the UI using this value as a correlation ID.
   */
  public static final String DEBUG_ID_HEADER_KEY = "jaeger-debug-id";

  /**
   * The name of HTTP header or a TextMap carrier key that can be used to pass
   * additional baggage to the span, e.g. when executing an ad-hoc curl request:
   * <pre>
   * curl -H 'jaeger-baggage: k1=v1,k2=v2' http://...
   * </pre>
   */
  public static final String BAGGAGE_HEADER_KEY = "jaeger-baggage";

  /**
   * The name of the tag used to report client version.
   */
  public static final String JAEGER_CLIENT_VERSION_TAG_KEY = "jaeger.version";

  /**
   * The name used to report host name of the process.
   */
  public static final String TRACER_HOSTNAME_TAG_KEY = "hostname";

  /**
   * The name used to report ip of the process.
   */
  public static final String TRACER_IP_TAG_KEY = "ip";
}
