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

package io.jaegertracing.thrift.internal.reporters.protocols;

import io.jaegertracing.internal.JaegerSpan;
import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.internal.LogData;
import io.jaegertracing.internal.Reference;
import io.jaegertracing.thriftjava.Log;
import io.jaegertracing.thriftjava.SpanRef;
import io.jaegertracing.thriftjava.SpanRefType;
import io.jaegertracing.thriftjava.Tag;
import io.jaegertracing.thriftjava.TagType;
import io.opentracing.References;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JaegerThriftSpanConverter {

  private JaegerThriftSpanConverter() {}

  public static io.jaegertracing.thriftjava.Span convertSpan(JaegerSpan jaegerSpan) {
    JaegerSpanContext context = jaegerSpan.context();

    boolean oneChildOfParent = jaegerSpan.getReferences().size() == 1
        && References.CHILD_OF.equals(jaegerSpan.getReferences().get(0).getType());

    List<SpanRef> references = oneChildOfParent
        ? Collections.<SpanRef>emptyList()
        : buildReferences(jaegerSpan.getReferences());

    return new io.jaegertracing.thriftjava.Span(
        context.getTraceIdLow(),
        context.getTraceIdHigh(),
        context.getSpanId(),
        oneChildOfParent ? context.getParentId() : 0,
        jaegerSpan.getOperationName(),
        context.getFlags(),
        jaegerSpan.getStart(),
        jaegerSpan.getDuration()
    )
        .setReferences(references)
        .setTags(buildTags(jaegerSpan.getTags()))
        .setLogs(buildLogs(jaegerSpan.getLogs()));
  }

  static List<SpanRef> buildReferences(List<Reference> references) {
    List<SpanRef> thriftReferences = new ArrayList<SpanRef>(references.size());
    for (Reference reference: references) {
      SpanRefType thriftRefType = References.CHILD_OF.equals(reference.getType()) ? SpanRefType.CHILD_OF :
              SpanRefType.FOLLOWS_FROM;
      thriftReferences.add(new SpanRef(thriftRefType, reference.getSpanContext().getTraceIdLow(),
              reference.getSpanContext().getTraceIdHigh(), reference.getSpanContext().getSpanId()));
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
