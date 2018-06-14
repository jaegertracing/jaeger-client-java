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

package io.jaegertracing.thrift.reporters.protocols;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import io.jaegertracing.Reference;
import io.jaegertracing.SpanContext;
import io.jaegertracing.Tracer;
import io.jaegertracing.reporters.InMemoryReporter;
import io.jaegertracing.samplers.ConstSampler;
import io.jaegertracing.thriftjava.Log;
import io.jaegertracing.thriftjava.SpanRef;
import io.jaegertracing.thriftjava.Tag;
import io.jaegertracing.thriftjava.TagType;
import io.opentracing.References;
import io.opentracing.Span;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class JaegerThriftSpanConverterTest {
  Tracer tracer;

  @Before
  public void setUp() {
    tracer = new Tracer.Builder("test-service-name")
            .withReporter(new InMemoryReporter())
            .withSampler(new ConstSampler(true))
            .build();
  }

  @DataProvider
  public static Object[][] dataProviderBuildTag() {
    return new Object[][] {
        { "value", TagType.STRING, "value" },
        { (long) 1, TagType.LONG, (long) 1 },
        { 1, TagType.LONG, (long) 1 },
        { (short) 1, TagType.LONG, (long) 1 },
        { (double) 1, TagType.DOUBLE, (double) 1 },
        { (float) 1, TagType.DOUBLE, (double) 1 },
        { (byte) 1, TagType.STRING, "1" },
        { true, TagType.BOOL, true },
        { new ArrayList<String>() {
            {
              add("hello");
            }
          }, TagType.STRING, "[hello]" }
    };
  }

  @Test
  @UseDataProvider("dataProviderBuildTag")
  public void testBuildTag(Object tagValue, TagType tagType, Object expected) {
    Tag tag = JaegerThriftSpanConverter.buildTag("key", tagValue);
    assertEquals(tagType, tag.getVType());
    assertEquals("key", tag.getKey());
    switch (tagType) {
      case STRING:
      default:
        assertEquals(expected, tag.getVStr());
        break;
      case BOOL:
        assertEquals(expected, tag.isVBool());
        break;
      case LONG:
        assertEquals(expected, tag.getVLong());
        break;
      case DOUBLE:
        assertEquals(expected, tag.getVDouble());
        break;
      case BINARY:
        break;
    }
  }

  @Test
  public void testBuildTags() {
    Map<String, Object> tags = new HashMap<String, Object>();
    tags.put("key", "value");

    List<Tag> thriftTags = JaegerThriftSpanConverter.buildTags(tags);
    assertNotNull(thriftTags);
    assertEquals(1, thriftTags.size());
    assertEquals("key", thriftTags.get(0).getKey());
    assertEquals("value", thriftTags.get(0).getVStr());
    assertEquals(TagType.STRING, thriftTags.get(0).getVType());
  }

  @Test
  public void testConvertSpan() {
    Map<String, Object> fields = new HashMap<String, Object>();
    fields.put("k", "v");

    Span span = tracer.buildSpan("operation-name").start();
    span = span.log(1, fields);
    span = span.setBaggageItem("foo", "bar");

    io.jaegertracing.thriftjava.Span thriftSpan = JaegerThriftSpanConverter.convertSpan((io.jaegertracing.Span) span);

    assertEquals("operation-name", thriftSpan.getOperationName());
    assertEquals(2, thriftSpan.getLogs().size());
    Log thriftLog = thriftSpan.getLogs().get(0);
    assertEquals(1, thriftLog.getTimestamp());
    assertEquals(1, thriftLog.getFields().size());
    Tag thriftTag = thriftLog.getFields().get(0);
    assertEquals("k", thriftTag.getKey());
    assertEquals("v", thriftTag.getVStr());

    thriftLog = thriftSpan.getLogs().get(1);
    assertEquals(3, thriftLog.getFields().size());
    thriftTag = thriftLog.getFields().get(0);
    assertEquals("event", thriftTag.getKey());
    assertEquals("baggage", thriftTag.getVStr());
    thriftTag = thriftLog.getFields().get(1);
    assertEquals("value", thriftTag.getKey());
    assertEquals("bar", thriftTag.getVStr());
    thriftTag = thriftLog.getFields().get(2);
    assertEquals("key", thriftTag.getKey());
    assertEquals("foo", thriftTag.getVStr());
  }

  @Test
  public void testConvertSpanOneReferenceChildOf() {
    Span parent = tracer.buildSpan("foo").start();

    Span child = tracer.buildSpan("foo")
        .asChildOf(parent)
        .start();

    io.jaegertracing.thriftjava.Span span = JaegerThriftSpanConverter.convertSpan((io.jaegertracing.Span) child);

    assertEquals(((io.jaegertracing.SpanContext)child.context()).getParentId(), span.getParentSpanId());
    assertEquals(0, span.getReferences().size());
  }

  @Test
  public void testConvertSpanTwoReferencesChildOf() {
    Span parent = tracer.buildSpan("foo").start();
    Span parent2 = tracer.buildSpan("foo").start();

    Span child = tracer.buildSpan("foo")
        .asChildOf(parent)
        .asChildOf(parent2)
        .start();

    io.jaegertracing.thriftjava.Span span = JaegerThriftSpanConverter.convertSpan((io.jaegertracing.Span) child);

    assertEquals(0, span.getParentSpanId());
    assertEquals(2, span.getReferences().size());
    assertEquals(buildReference((SpanContext) parent.context(), References.CHILD_OF),span.getReferences().get(0));
    assertEquals(buildReference((SpanContext) parent2.context(), References.CHILD_OF),span.getReferences().get(1));
  }

  @Test
  public void testConvertSpanMixedReferences() {
    Span parent = tracer.buildSpan("foo").start();
    Span parent2 = tracer.buildSpan("foo").start();

    Span child = tracer.buildSpan("foo")
        .addReference(References.FOLLOWS_FROM, parent.context())
        .asChildOf(parent2)
        .start();

    io.jaegertracing.thriftjava.Span span = JaegerThriftSpanConverter.convertSpan((io.jaegertracing.Span) child);

    assertEquals(0, span.getParentSpanId());
    assertEquals(2, span.getReferences().size());
    assertEquals(buildReference((SpanContext) parent.context(), References.FOLLOWS_FROM),span.getReferences().get(0));
    assertEquals(buildReference((SpanContext) parent2.context(), References.CHILD_OF),span.getReferences().get(1));
  }

  private static SpanRef buildReference(SpanContext context, String referenceType) {
    return JaegerThriftSpanConverter.buildReferences(
        Collections.singletonList(new Reference(context, referenceType)))
        .get(0);
  }
}
