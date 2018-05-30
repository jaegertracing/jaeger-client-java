/*
 * Copyright (c) 2018, The Jaeger Authors
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

package io.jaegertracing.zipkin;

import io.jaegertracing.JaegerSpan;
import io.opentracing.tag.Tags;

/**
 * Logic that is common to both Thrift v1 and JSON v2 senders
 */
public class ConverterUtil {
  public static boolean isRpcServer(JaegerSpan jaegerSpan) {
    return Tags.SPAN_KIND_SERVER.equals(jaegerSpan.getTags().get(Tags.SPAN_KIND.getKey()));
  }

  public static boolean isRpc(JaegerSpan jaegerSpan) {
    return isRpcServer(jaegerSpan) || isRpcClient(jaegerSpan);
  }

  public static boolean isRpcClient(JaegerSpan jaegerSpan) {
    return Tags.SPAN_KIND_CLIENT.equals(jaegerSpan.getTags().get(Tags.SPAN_KIND.getKey()));
  }
}
