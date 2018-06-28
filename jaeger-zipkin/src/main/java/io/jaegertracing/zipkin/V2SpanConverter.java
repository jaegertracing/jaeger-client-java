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

import com.google.gson.Gson;
import io.jaegertracing.Constants;
import io.jaegertracing.JaegerSpan;
import io.jaegertracing.JaegerSpanContext;
import io.jaegertracing.JaegerTracer;
import io.jaegertracing.LogData;
import io.opentracing.tag.Tags;
import java.util.List;
import java.util.Map;

/**
 * Converts a Jaeger span to a Zipkin2 span.
 */
public class V2SpanConverter {

  private static final Gson gson = new Gson();

  public static zipkin2.Span convertSpan(JaegerSpan span) {
    JaegerTracer tracer = span.getTracer();

    zipkin2.Endpoint.Builder host = zipkin2.Endpoint.newBuilder()
        .serviceName(tracer.getServiceName());
    if (tracer.getIpv4() != 0) {
      host.parseIp(convertIp(tracer.getIpv4()));
    }

    zipkin2.Endpoint peerEndpoint = extractPeerEndpoint(span.getTags());

    JaegerSpanContext context = span.context();
    zipkin2.Span.Builder builder = zipkin2.Span.newBuilder()
            .id(Long.toHexString(context.getSpanId()))
            .traceId(Long.toHexString(context.getTraceId()))
            .name(span.getOperationName())
            .parentId(Long.toHexString(context.getParentId()))
            .debug(context.isDebug())
            .localEndpoint(host.build())
            .remoteEndpoint(peerEndpoint)
            .kind(convertKind(span.getTags().get(Tags.SPAN_KIND.getKey())))
            .timestamp(span.getStart())
            .duration(span.getDuration());

    buildAnnotations(span, builder);
    buildTags(span, builder);

    return builder.build();
  }

  private static zipkin2.Span.Kind convertKind(Object kind) {
    if (Tags.SPAN_KIND_SERVER.equals(kind)) {
      return zipkin2.Span.Kind.SERVER;
    } else if (Tags.SPAN_KIND_CLIENT.equals(kind)) {
      return zipkin2.Span.Kind.CLIENT;
    } else if (Tags.SPAN_KIND_CONSUMER.equals(kind)) {
      return zipkin2.Span.Kind.CONSUMER;
    } else if (Tags.SPAN_KIND_PRODUCER.equals(kind)) {
      return zipkin2.Span.Kind.PRODUCER;
    } else {
      return null;
    }
  }

  private static void buildAnnotations(JaegerSpan jaegerSpan, zipkin2.Span.Builder builder) {
    List<LogData> logs = jaegerSpan.getLogs();
    if (logs != null) {
      for (LogData logData : logs) {
        String logMessage = logData.getMessage();
        Map<String, ?> logFields = logData.getFields();
        if (logMessage != null) {
          builder.addAnnotation(logData.getTime(), logMessage);
        } else if (logFields != null) {
          builder.addAnnotation(logData.getTime(), gson.toJson(logFields));
        }
      }
    }
  }

  private static void buildTags(JaegerSpan jaegerSpan, zipkin2.Span.Builder builder) {
    Map<String, Object> tags = jaegerSpan.getTags();
    boolean firstSpanInProcess = jaegerSpan.getReferences().isEmpty() || ConverterUtil.isRpcServer(jaegerSpan);

    if (firstSpanInProcess) {
      Map<String, ?> processTags = jaegerSpan.getTracer().tags();
      // add tracer tags to first zipkin span in a process but remove "ip" tag as it is
      // taken care of separately.
      for (Map.Entry<String, ?> entry : processTags.entrySet()) {
        String tagKey = entry.getKey();
        if (!Constants.TRACER_IP_TAG_KEY.equals(tagKey)) {
          Object tagValue = entry.getValue();
          // add a tracer. prefix to process tags for zipkin
          builder.putTag("tracer." + tagKey, tagValue.toString());
        }
      }
    }

    if (tags != null) {
      for (Map.Entry<String, Object> entry : tags.entrySet()) {
        String tagKey = entry.getKey();
        // Every value is converted to string because zipkin search doesn't
        // work well with ints, and bytes.
        Object tagValue = entry.getValue();
        builder.putTag(tagKey, tagValue.toString());
      }
    }
  }

  private static byte[] convertIp(int ipv4) {
    return new byte[] {
        (byte) (ipv4 >> 24 & 0xff),
        (byte) (ipv4 >> 16 & 0xff),
        (byte) (ipv4 >> 8 & 0xff),
        (byte) (ipv4 & 0xff)
    };
  }

  /**
   * Extract peer Endpoint from tags
   *
   * @param tags tags
   * @return null or peer endpoint
   */
  public static zipkin2.Endpoint extractPeerEndpoint(Map<String, Object> tags) {
    Object peerIpv4 = tags.get(Tags.PEER_HOST_IPV4.getKey());
    Object peerPort = tags.get(Tags.PEER_PORT.getKey());
    Object peerService = tags.get(Tags.PEER_SERVICE.getKey());

    if (peerIpv4 == null && peerPort == null && peerService == null) {
      return null;
    }

    zipkin2.Endpoint.Builder builder = zipkin2.Endpoint.newBuilder();

    if (peerIpv4 instanceof String) {
      builder.ip((String) peerIpv4);
    }
    if (peerPort instanceof Number) {
      builder.port(((Number) peerPort).intValue());
    }
    if (peerService instanceof String) {
      builder.serviceName((String) peerService);
    }

    return builder.build();
  }
}
