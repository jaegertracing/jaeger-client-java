/*
 * Copyright (c) 2017, The Jaeger Authors
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

package com.uber.jaeger.context;

import io.opentracing.ActiveSpanSource;
import io.opentracing.Span;
import io.opentracing.util.ThreadLocalActiveSpanSource;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ActiveSpanSourceTraceContextTest {

  private ActiveSpanSource activeSpanSource = new ThreadLocalActiveSpanSource();

  @Mock private Span span;

  private ActiveSpanSourceTraceContext traceContext =
      new ActiveSpanSourceTraceContext(activeSpanSource);

  @Test
  public void pushAndPop() throws Exception {
    traceContext.push(span);
    Span actual = traceContext.pop();
    Assert.assertEquals(span, actual);
  }

  @Test
  public void popNull() throws Exception {
    Span actual = traceContext.pop();
    Assert.assertNull(actual);
  }

  @Test
  public void getCurrentSpan() throws Exception {
    traceContext.push(span);
    Span actual = traceContext.getCurrentSpan();
    Assert.assertEquals(span, actual);
  }

  @Test
  public void getCurrentSpanNull() throws Exception {
    Span actual = traceContext.getCurrentSpan();
    Assert.assertNull(actual);
  }

  @Test
  public void isEmpty() throws Exception {
    Assert.assertTrue(traceContext.isEmpty());
  }
}
