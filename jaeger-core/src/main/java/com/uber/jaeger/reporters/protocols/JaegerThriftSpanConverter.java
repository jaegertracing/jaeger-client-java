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

import com.uber.jaeger.Constants;
import com.uber.jaeger.LogData;
import com.uber.jaeger.Span;
import com.uber.jaeger.SpanContext;
import com.uber.jaeger.thriftjava.Log;
import com.uber.jaeger.thriftjava.Tag;
import com.uber.jaeger.thriftjava.TagType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JaegerThriftSpanConverter {

  public static com.uber.jaeger.thriftjava.Span convertSpan(Span span) {
    SpanContext context = span.context();

    return new com.uber.jaeger.thriftjava.Span(
            context.getTraceID(),
            0, // TraceIdHigh is currently not supported
            context.getSpanID(),
            context.getParentID(),
            span.getOperationName(),
            context.getFlags(),
            span.getStart(),
            span.getDuration()
        )
        .setTags(buildTags(span.getTags()))
        .setLogs(buildLogs(span.getLogs()));
  }

  protected static List<Log> buildLogs(List<LogData> logs) {
    List<Log> jLogs = new ArrayList<>();
    if (logs != null) {
      for (LogData logData : logs) {
        Log jLog = new Log();
        jLog.setTimestamp(logData.getTime());
        final Tag tag = buildTag(logData.getMessage(), logData.getPayload());
        jLog.setFields(new ArrayList<Tag>() {{add(tag);}});
        jLogs.add(jLog);
      }
    }
    return jLogs;
  }

  protected static List<Tag> buildTags(Map<String, Object> tags) {
    List<Tag> jTags = new ArrayList<>();
    if (tags != null) {
      for (Map.Entry<String, Object> entry : tags.entrySet()) {
        String tagKey = entry.getKey();
        Object tagValue = entry.getValue();
        jTags.add(buildTag(tagKey, tagValue));
      }
    }
    return jTags;
  }

  protected static Tag buildTag(String tagKey, Object tagValue) {
    Tag tag = new Tag();
    tag.setKey(tagKey);
    if (tagValue instanceof Integer || tagValue instanceof Short || tagValue instanceof Long) {
      tag.setVType(TagType.LONG);
      tag.setVLong(((Number) tagValue).longValue());
    } else if (tagValue instanceof Double || tagValue instanceof Float) {
      tag.setVType(TagType.DOUBLE);
      tag.setVDouble(((Number)tagValue).doubleValue());
    } else if (tagValue instanceof Boolean) {
      tag.setVType(TagType.BOOL);
      tag.setVBool((Boolean) tagValue);
    } else {
      buildStringTag(tag, tagValue);
    }
    return tag;
  }

  protected static void buildStringTag(Tag tag, Object tagValue) {
    tag.setVType(TagType.STRING);
    String stringTagValue = String.valueOf(tagValue);
    tag.setVStr(truncateString(stringTagValue));
  }

  protected static String truncateString(String s) {
    if (s.length() > Constants.MAX_TAG_LENGTH) {
      return s.substring(0, Constants.MAX_TAG_LENGTH);
    }
    return s;
  }
}
