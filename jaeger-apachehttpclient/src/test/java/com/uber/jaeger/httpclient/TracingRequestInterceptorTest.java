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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.Tracer;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(TracingRequestInterceptor.class)
public class TracingRequestInterceptorTest {

  @Test
  public void testProcessNullScope()
      throws Exception {
    ScopeManager mockScopeManager = Mockito.mock(ScopeManager.class);
    when(mockScopeManager.active()).thenReturn(null);

    Tracer mockTracer = Mockito.mock(Tracer.class);
    when(mockTracer.scopeManager()).thenReturn(mockScopeManager);

    HttpRequestInterceptor interceptor = new TracingRequestInterceptor(mockTracer);
    PowerMockito.spy(interceptor);

    HttpRequest mockRequest = Mockito.mock(HttpRequest.class);
    HttpContext mockContext = Mockito.mock(HttpContext.class);

    interceptor.process(mockRequest, mockContext);
    PowerMockito.verifyPrivate(interceptor, times(0))
        .invoke("onSpanStarted", any(Span.class), mockRequest, mockContext);
  }
}
