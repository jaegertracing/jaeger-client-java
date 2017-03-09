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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ThriftSpanConverter {

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
    List<Annotation> annotations = new ArrayList<>();

    if (span.isRPC()) {
      String startLabel = zipkincoreConstants.SERVER_RECV;
      String endLabel = zipkincoreConstants.SERVER_SEND;
      if (span.isRPCClient()) {
        startLabel = zipkincoreConstants.CLIENT_SEND;
        endLabel = zipkincoreConstants.CLIENT_RECV;
      }

      annotations.add(new Annotation(span.getStart(), startLabel).setHost(host));
      annotations.add(new Annotation(span.getStart() + span.getDuration(), endLabel).setHost(host));
    }

    List<LogData> logs = span.getLogs();
    if (logs != null) {
      for (LogData logData : logs) {
        annotations.add(new Annotation(logData.getTime(), logData.getMessage()));
      }
    }

    return annotations;
  }

  private static List<BinaryAnnotation> buildBinaryAnnotations(Span span, Endpoint host) {
    List<BinaryAnnotation> binaryAnnotations = new ArrayList<BinaryAnnotation>();

    if (span.getPeer() != null && span.isRPC()) {
      String label =
          span.isRPCClient() ? zipkincoreConstants.SERVER_ADDR : zipkincoreConstants.CLIENT_ADDR;

      binaryAnnotations.add(
          new BinaryAnnotation()
              .setKey(label)
              .setValue(new byte[] {1})
              .setAnnotation_type(AnnotationType.BOOL)
              .setHost(span.getPeer()));
    }

    if (!span.isRPC()) {
      byte[] componentName;
      if (span.getLocalComponent() != null) {
        componentName = span.getLocalComponent().getBytes(StandardCharsets.UTF_8);
      } else {
        // spans always have associated tracers, and service names
        componentName = span.getTracer().getServiceName().getBytes(StandardCharsets.UTF_8);
      }

      binaryAnnotations.add(
          new BinaryAnnotation()
              .setKey(zipkincoreConstants.LOCAL_COMPONENT)
              .setValue(componentName)
              .setAnnotation_type(AnnotationType.STRING)
              .setHost(host));
    }

    Map<String, Object> tags = span.getTags();
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

    banno
        .setValue(stringTagValue.getBytes(StandardCharsets.UTF_8))
        .setAnnotation_type(AnnotationType.STRING);

    return banno;
  }
}
