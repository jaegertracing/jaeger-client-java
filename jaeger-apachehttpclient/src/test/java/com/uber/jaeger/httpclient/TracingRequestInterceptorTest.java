/*
 * Copyright (c) 2018, Uber Technologies, Inc
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

package com.uber.jaeger.httpclient;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static uk.org.lidalia.slf4jtest.LoggingEvent.error;
import static uk.org.lidalia.slf4jtest.LoggingEvent.warn;

import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.Tracer;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import uk.org.lidalia.slf4jtest.TestLogger;
import uk.org.lidalia.slf4jtest.TestLoggerFactory;

@RunWith(PowerMockRunner.class)
@PrepareForTest(TracingRequestInterceptor.class)
public class TracingRequestInterceptorTest {

  TestLogger logger = TestLoggerFactory.getTestLogger(TracingRequestInterceptor.class);

  @Test
  public void testProcessNoErrors() throws IOException, HttpException {
    ScopeManager mockScopeManager = Mockito.mock(ScopeManager.class);
    when(mockScopeManager.active()).thenReturn(null);

    Tracer mockTracer = Mockito.mock(Tracer.class);
    when(mockTracer.scopeManager()).thenReturn(mockScopeManager);

    HttpRequestInterceptor interceptor = new TracingRequestInterceptor(mockTracer);

    HttpRequest mockRequest = Mockito.mock(HttpRequest.class);
    HttpContext mockContext = Mockito.mock(HttpContext.class);

    Span mockSpan = Mockito.mock(Span.class);

    // make sure that mockContext does not return a null span
    when(mockContext.getAttribute(Constants.CURRENT_SPAN_CONTEXT_KEY)).thenReturn(mockSpan);

    interceptor.process(mockRequest, mockContext);

    assertThat(
        logger.getLoggingEvents(),
        is(new ArrayList<>())
    );
  }

  @Test
  public void testProcessNoClientSpanInContext()
      throws IOException, HttpException {
    ScopeManager mockScopeManager = Mockito.mock(ScopeManager.class);
    when(mockScopeManager.active()).thenReturn(null);

    Tracer mockTracer = Mockito.mock(Tracer.class);
    when(mockTracer.scopeManager()).thenReturn(mockScopeManager);

    HttpRequestInterceptor interceptor = new TracingRequestInterceptor(mockTracer);

    HttpRequest mockRequest = Mockito.mock(HttpRequest.class);
    HttpContext mockContext = Mockito.mock(HttpContext.class);

    // make sure that mockContext returns a null span
    when(mockContext.getAttribute(Constants.CURRENT_SPAN_CONTEXT_KEY)).thenReturn(null);

    interceptor.process(mockRequest, mockContext);

    assertThat(
        logger.getLoggingEvents(),
        is(asList(warn("Current client span not found in http context.")))
    );
  }

  @Test
  public void testProcessSpanCreationException()
      throws NoSuchFieldException, IllegalAccessException, IOException, HttpException {
    ScopeManager mockScopeManager = Mockito.mock(ScopeManager.class);
    when(mockScopeManager.active()).thenReturn(null);

    Tracer mockTracer = Mockito.mock(Tracer.class);
    when(mockTracer.scopeManager()).thenReturn(mockScopeManager);

    HttpRequest mockRequest = Mockito.mock(HttpRequest.class);
    HttpContext mockContext = Mockito.mock(HttpContext.class);

    SpanCreationRequestInterceptor mockSpanCreationInterceptor = Mockito
        .mock(SpanCreationRequestInterceptor.class);

    Exception spanCreationException = new IOException("Failed to create span");
    doThrow(spanCreationException)
        .when(mockSpanCreationInterceptor)
        .process(mockRequest, mockContext);

    HttpRequestInterceptor interceptor = new TracingRequestInterceptor(mockTracer);
    Field spanCreationInterceptorField = interceptor
        .getClass()
        .getDeclaredField("spanCreationInterceptor");
    spanCreationInterceptorField.setAccessible(true);
    spanCreationInterceptorField.set(interceptor, mockSpanCreationInterceptor);

    interceptor.process(mockRequest, mockContext);

    assertThat(
        logger.getLoggingEvents(),
        is(asList(error(spanCreationException, "Could not start client tracing span.")))
    );
  }

  @After
  public void clearLoggers() {
    TestLoggerFactory.clear();
  }
}
