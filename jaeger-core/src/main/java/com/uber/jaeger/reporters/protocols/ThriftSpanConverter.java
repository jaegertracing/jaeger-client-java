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
package com.uber.jaeger.reporters.protocols;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

public class ThriftSpanConverter {

  private static final Charset UTF_8 = Charset.forName("UTF-8");

  public static com.twitter.zipkin.thriftjava.Span convertSpan(Span span) {
    Tracer tracer = span.getTracer();
    Endpoint host = new Endpoint(tracer.getIP(), (short) 0, tracer.getServiceName());

  SpanContext context = span.context();
    return new com.twitter.zipkin.thriftjava.Span(
            context.getTraceID(),
            span.getOperationName(),
            context.getSpanID(),
            buildAnnotations(span, host),
            buildBinaryAnnotations(span, host))
        .setParent_id(context.getParentID())
        .setDebug(context.isDebug())
        .setTimestamp(span.getStart())
        .setDuration(span.getDuration());
  }

  private static List<Annotation> buildAnnotations(Span span, Endpoint host) {
    List<Annotation> annotations = new ArrayList<Annotation>();

    if (isRPC(span)) {
      String startLabel = zipkincoreConstants.SERVER_RECV;
      String endLabel = zipkincoreConstants.SERVER_SEND;
      if (isRPCClient(span)) {
        startLabel = zipkincoreConstants.CLIENT_SEND;
        endLabel = zipkincoreConstants.CLIENT_RECV;
      }

      annotations.add(new Annotation(span.getStart(), startLabel).setHost(host));
      annotations.add(new Annotation(span.getStart() + span.getDuration(), endLabel).setHost(host));
    }

    List<LogData> logs = span.getLogs();
    if (logs != null) {
      for (LogData logData : logs) {
        // skip if log is empty
        if (logData.getMessage() == null && logData.getPayload() == null &&
                (logData.getFields() == null || logData.getFields().isEmpty())) {
          continue;
        }

        String value = logData.getMessage();
        if (logData.getPayload() != null) {
          value = logData.getPayload().toString();
        } else if (logData.getFields() != null && !logData.getFields().isEmpty()) {
          // special-case the "event" field which is similar to the semantics of a zipkin annotation
          Object event = logData.getFields().get("event");
          if (event != null && logData.getFields().size() == 1) {
            value  = event.toString();
          } else {
            StringBuilder result = new StringBuilder();
            for (Iterator<? extends Map.Entry<String, ?>> i = logData.getFields().entrySet().iterator(); i.hasNext(); ) {
              Map.Entry<String, ?> next = i.next();
              result.append(next.getKey()).append('=').append(next.getValue());
              if (i.hasNext()) result.append(' ');
            }
            value = result.toString();
          }
        }

        annotations.add(new Annotation(logData.getTime(), value));
      }
    }

    return annotations;
  }

  private static List<BinaryAnnotation> buildBinaryAnnotations(Span span, Endpoint host) {
    List<BinaryAnnotation> binaryAnnotations = new ArrayList<BinaryAnnotation>();
    Map<String, Object> tags = span.getTags();
    boolean isRpc = isRPC(span);
    boolean isClient = isRPCClient(span);

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
        componentName = componentTag.toString().getBytes();
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

    String stringTagValue = tagValue.toString();
    if (stringTagValue.length() > Constants.MAX_TAG_LENGTH) {
      tagValue = stringTagValue.substring(0, Constants.MAX_TAG_LENGTH);
    }

    banno.setValue(stringTagValue.getBytes(UTF_8)).setAnnotation_type(AnnotationType.STRING);

    return banno;
  }

  public static boolean isRPC(Span span) {
    Object spanKindValue = span.getTags().get(Tags.SPAN_KIND.getKey());
    return Tags.SPAN_KIND_CLIENT.equals(spanKindValue) || Tags.SPAN_KIND_SERVER.equals(spanKindValue);

  }

  public static boolean isRPCClient(Span span) {
    return Tags.SPAN_KIND_CLIENT.equals(span.getTags().get(Tags.SPAN_KIND.getKey()));
  }

  /**
   * Extract peer Endpoint from tags
   *
   * @param tags tags
   * @return null or peer endpoint
   */
  public static Endpoint extractPeerEndpoint(Map<String, Object> tags) {
    Object tagValue = tags.get(Tags.PEER_HOST_IPV4.getKey());
    Endpoint peerEndpoint = null;
    if (tagValue instanceof Integer) {
      peerEndpoint = new Endpoint((Integer) tagValue, (short) 0, "");
    }
    tagValue = tags.get(Tags.PEER_PORT.getKey());
    if (tagValue instanceof Number) {
      if (peerEndpoint == null) { peerEndpoint = new Endpoint(0, (short) 0, ""); }
      peerEndpoint.setPort(((Number)tagValue).shortValue());
    }
    tagValue = tags.get(Tags.PEER_SERVICE.getKey());
    if (tagValue instanceof String) {
      if (peerEndpoint == null) { peerEndpoint = new Endpoint(0, (short) 0, ""); }
      peerEndpoint.setService_name((String) tagValue);
    }

    return peerEndpoint;
  }
}
