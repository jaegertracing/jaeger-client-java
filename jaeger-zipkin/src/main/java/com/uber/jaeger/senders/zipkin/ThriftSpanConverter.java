/*
 * Copyright (c) 2016, Uber Technologies, Inc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.uber.jaeger.senders.zipkin;

import com.twitter.zipkin.thriftjava.Annotation;
import com.twitter.zipkin.thriftjava.AnnotationType;
import com.twitter.zipkin.thriftjava.BinaryAnnotation;
import com.twitter.zipkin.thriftjava.Endpoint;
import com.twitter.zipkin.thriftjava.zipkincoreConstants;
import com.uber.jaeger.Constants;
import com.uber.jaeger.LogData;
import com.uber.jaeger.Span;
import com.uber.jaeger.SpanContext;
import com.uber.jaeger.Tracer;
import io.opentracing.tag.Tags;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ThriftSpanConverter {

  private static final Charset UTF_8 = Charset.forName("UTF-8");

  public static com.twitter.zipkin.thriftjava.Span convertSpan(Span span) {
    Tracer tracer = span.getTracer();
    Endpoint host = new Endpoint(tracer.getIpv4(), (short) 0, tracer.getServiceName());

    SpanContext context = span.context();
    return new com.twitter.zipkin.thriftjava.Span(
            context.getTraceId(),
            span.getOperationName(),
            context.getSpanId(),
            buildAnnotations(span, host),
            buildBinaryAnnotations(span, host))
        .setParent_id(context.getParentId())
        .setDebug(context.isDebug())
        .setTimestamp(span.getStart())
        .setDuration(span.getDuration());
  }

  private static List<Annotation> buildAnnotations(Span span, Endpoint host) {
    List<Annotation> annotations = new ArrayList<Annotation>();

    if (isRpc(span)) {
      String startLabel = zipkincoreConstants.SERVER_RECV;
      String endLabel = zipkincoreConstants.SERVER_SEND;
      if (isRpcClient(span)) {
        startLabel = zipkincoreConstants.CLIENT_SEND;
        endLabel = zipkincoreConstants.CLIENT_RECV;
      }

      annotations.add(new Annotation(span.getStart(), startLabel).setHost(host));
      annotations.add(new Annotation(span.getStart() + span.getDuration(), endLabel).setHost(host));
    }

    List<LogData> logs = span.getLogs();
    if (logs != null) {
      for (LogData logData : logs) {
        String logMessage = logData.getMessage();
        Map<String, ?> logFields = logData.getFields();
        if (logMessage != null) {
          annotations.add(new Annotation(logData.getTime(), logMessage));
        } else if (logFields != null) {
          annotations.add(new Annotation(logData.getTime(), logFieldsAsMessage(logFields)));
        }
      }
    }

    return annotations;
  }

  private static String logFieldsAsMessage(Map<String, ?> logFields) {
    StringBuilder message = new StringBuilder();
    String delimiter = "";
    for (Map.Entry<String, ?> field : logFields.entrySet()) {
      message.append(delimiter);
      message.append(field.getKey());
      message.append("=");
      Object fieldValue = field.getValue();
      if (fieldValue instanceof String) {
        message.append("\"");
        message.append((String) fieldValue);
        message.append("\"");
      } else {
        message.append(String.valueOf(fieldValue));
      }
      delimiter = " ";
    }
    return message.toString();
  }

  private static List<BinaryAnnotation> buildBinaryAnnotations(Span span, Endpoint host) {
    List<BinaryAnnotation> binaryAnnotations = new ArrayList<BinaryAnnotation>();
    Map<String, Object> tags = span.getTags();
    boolean isRpc = isRpc(span);
    boolean isClient = isRpcClient(span);
    boolean firstSpanInProcess = span.getReferences().isEmpty() || isRpcServer(span);

    if (firstSpanInProcess) {
      Map<String, ?> processTags = span.getTracer().tags();
      // add tracer tags to first zipkin span in a process but remove "ip" tag as it is
      // taken care of separately.
      for (String tagKey : processTags.keySet()) {
        if (!tagKey.equals(Constants.TRACER_IP_TAG_KEY)) {
          Object tagValue = processTags.get(tagKey);
          // add a tracer. prefix to process tags for zipkin
          binaryAnnotations.add(buildBinaryAnnotation("tracer." + tagKey, tagValue));
        }
      }
    }

    Endpoint peerEndpoint = extractPeerEndpoint(tags);
    if (peerEndpoint != null && isClient) {
      String key =
          isClient ? zipkincoreConstants.SERVER_ADDR : zipkincoreConstants.CLIENT_ADDR;

      binaryAnnotations.add(
          new BinaryAnnotation()
              .setKey(key)
              .setValue(new byte[] {1})
              .setAnnotation_type(AnnotationType.BOOL)
              .setHost(peerEndpoint));
    }

    if (!isRpc) {
      byte[] componentName;
      Object componentTag = tags.get(Tags.COMPONENT.getKey());
      if (componentTag instanceof String) {
        componentName = componentTag.toString().getBytes(UTF_8);
      } else {
        // spans always have associated tracers, and service names
        componentName = span.getTracer().getServiceName().getBytes(UTF_8);
      }

      binaryAnnotations.add(
          new BinaryAnnotation()
              .setKey(zipkincoreConstants.LOCAL_COMPONENT)
              .setValue(componentName)
              .setAnnotation_type(AnnotationType.STRING)
              .setHost(host));
    }

    if (tags != null) {
      for (String tagKey : tags.keySet()) {
        // Every value is converted to string because zipkin search doesn't
        // work well with ints, and bytes.
        Object tagValue = tags.get(tagKey);
        binaryAnnotations.add(buildBinaryAnnotation(tagKey, tagValue));
      }
    }
    return binaryAnnotations;
  }

  private static BinaryAnnotation buildBinaryAnnotation(String tagKey, Object tagValue) {
    BinaryAnnotation banno = new BinaryAnnotation().setKey(tagKey);
    banno.setValue(String.valueOf(tagValue).getBytes(UTF_8)).setAnnotation_type(AnnotationType.STRING);
    return banno;
  }

  static boolean isRpcServer(Span span) {
    return Tags.SPAN_KIND_SERVER.equals(span.getTags().get(Tags.SPAN_KIND.getKey()));
  }

  static boolean isRpc(Span span) {
    Object spanKindValue = span.getTags().get(Tags.SPAN_KIND.getKey());
    return Tags.SPAN_KIND_CLIENT.equals(spanKindValue) || Tags.SPAN_KIND_SERVER.equals(spanKindValue);

  }

  static boolean isRpcClient(Span span) {
    return Tags.SPAN_KIND_CLIENT.equals(span.getTags().get(Tags.SPAN_KIND.getKey()));
  }

  /**
   * Extract peer Endpoint from tags
   *
   * @param tags tags
   * @return null or peer endpoint
   */
  public static Endpoint extractPeerEndpoint(Map<String, Object> tags) {
    Object peerIpv4 = tags.get(Tags.PEER_HOST_IPV4.getKey());
    Object peerPort = tags.get(Tags.PEER_PORT.getKey());
    Object peerService = tags.get(Tags.PEER_SERVICE.getKey());

    if (peerIpv4 == null && peerPort == null && peerService == null) {
      return null;
    }

    Endpoint peerEndpoint = new Endpoint(0, (short) 0, "");

    if (peerIpv4 instanceof Integer) {
      peerEndpoint.setIpv4((Integer) peerIpv4);
    }
    if (peerPort instanceof Number) {
      peerEndpoint.setPort(((Number)peerPort).shortValue());
    }
    if (peerService instanceof String) {
      peerEndpoint.setService_name((String) peerService);
    }

    return peerEndpoint;
  }
}
