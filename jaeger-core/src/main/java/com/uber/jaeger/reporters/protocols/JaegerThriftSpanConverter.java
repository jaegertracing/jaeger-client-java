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

import com.uber.jaeger.LogData;
import com.uber.jaeger.Reference;
import com.uber.jaeger.Span;
import com.uber.jaeger.SpanContext;
import com.uber.jaeger.thriftjava.Log;
import com.uber.jaeger.thriftjava.SpanRef;
import com.uber.jaeger.thriftjava.SpanRefType;
import com.uber.jaeger.thriftjava.Tag;
import com.uber.jaeger.thriftjava.TagType;
import io.opentracing.References;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JaegerThriftSpanConverter {

  private JaegerThriftSpanConverter() {}

  public static com.uber.jaeger.thriftjava.Span convertSpan(Span span) {
    SpanContext context = span.context();

    boolean oneChildOfParent = span.getReferences().size() == 1
        && References.CHILD_OF.equals(span.getReferences().get(0).getType());

    return new com.uber.jaeger.thriftjava.Span(
            context.getTraceId(),
            0, // TraceIdHigh is currently not supported
            context.getSpanId(),
            oneChildOfParent ? context.getParentId() : 0,
            span.getOperationName(),
            context.getFlags(),
            span.getStart(),
            span.getDuration())
            .setReferences(oneChildOfParent ? Collections.<SpanRef>emptyList() : buildReferences(span.getReferences()))
        .setTags(buildTags(span.getTags()))
        .setLogs(buildLogs(span.getLogs()));
  }

  static List<SpanRef> buildReferences(List<Reference> references) {
    List<SpanRef> thriftReferences = new ArrayList<SpanRef>(references.size());
    for (Reference reference: references) {
      SpanRefType thriftRefType = References.CHILD_OF.equals(reference.getType()) ? SpanRefType.CHILD_OF :
              SpanRefType.FOLLOWS_FROM;
      thriftReferences.add(new SpanRef(thriftRefType, reference.getSpanContext().getTraceId(),
              0, reference.getSpanContext().getSpanId()));
    }

    return thriftReferences;
  }

  static List<Log> buildLogs(List<LogData> logs) {
    List<Log> thriftLogs = new ArrayList<Log>();
    if (logs != null) {
      for (LogData logData : logs) {
        Log thriftLog = new Log();
        thriftLog.setTimestamp(logData.getTime());
        if (logData.getFields() != null) {
          thriftLog.setFields(buildTags(logData.getFields()));
        } else {
          List<Tag> tags = new ArrayList<Tag>();
          if (logData.getMessage() != null) {
            tags.add(buildTag("event", logData.getMessage()));
          }
          if (logData.getPayload() != null) {
            tags.add(buildTag("payload", logData.getPayload()));
          }
          thriftLog.setFields(tags);
        }
        thriftLogs.add(thriftLog);
      }
    }
    return thriftLogs;
  }

  public static List<Tag> buildTags(Map<String, ?> tags) {
    List<Tag> thriftTags = new ArrayList<Tag>();
    if (tags != null) {
      for (Map.Entry<String, ?> entry : tags.entrySet()) {
        String tagKey = entry.getKey();
        Object tagValue = entry.getValue();
        thriftTags.add(buildTag(tagKey, tagValue));
      }
    }
    return thriftTags;
  }

  static Tag buildTag(String tagKey, Object tagValue) {
    Tag tag = new Tag();
    tag.setKey(tagKey);
    if (tagValue instanceof Integer || tagValue instanceof Short || tagValue instanceof Long) {
      tag.setVType(TagType.LONG);
      tag.setVLong(((Number) tagValue).longValue());
    } else if (tagValue instanceof Double || tagValue instanceof Float) {
      tag.setVType(TagType.DOUBLE);
      tag.setVDouble(((Number) tagValue).doubleValue());
    } else if (tagValue instanceof Boolean) {
      tag.setVType(TagType.BOOL);
      tag.setVBool((Boolean) tagValue);
    } else {
      buildStringTag(tag, tagValue);
    }
    return tag;
  }

  static void buildStringTag(Tag tag, Object tagValue) {
    tag.setVType(TagType.STRING);
    tag.setVStr(String.valueOf(tagValue));
  }
}
