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

package com.uber.jaeger.dropwizard;

import com.google.common.annotations.VisibleForTesting;
import com.uber.jaeger.context.TraceContext;
import com.uber.jaeger.filters.jaxrs2.ServerFilter;
import io.opentracing.Tracer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.ext.Provider;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.internal.routing.UriRoutingContext;
import org.glassfish.jersey.uri.UriTemplate;

/**
 * A {@link ServerFilter} that provides a Jersey specific {@link #getOperationName} implementation.
 * When using this Filter with Jersey, the full @Path template hierarchy is used as the operation
 * name.
 *
 * WARNING: This class relies on an private implementation detail
 * {@link UriTemplate#normalizedTemplate} to provide functionality.
 */
@Slf4j
@Provider
@Singleton
public class JerseyServerFilter extends ServerFilter {

  private Map<CacheKey, String> methodToPathCache = new ConcurrentHashMap<CacheKey, String>();
  private Field normalizedTemplate;

  public JerseyServerFilter(Tracer tracer, TraceContext traceContext) {
    super(tracer, traceContext);

    try {
      normalizedTemplate = UriTemplate.class.getDeclaredField("normalizedTemplate");
      normalizedTemplate.setAccessible(true);
    } catch (NoSuchFieldException e) {
      //TODO: Add metrics for failure counts
      log.error(
          "Cannot access the normalizedTemplate field from UriTemplate. "
              + "Path operation name for tracing will be disabled.",
          e);
    }
  }

  /**
   * Gets an operation name by first calling {@link #getPathOperationName(ContainerRequestContext)},
   * and falling back on {@link #getResourceMethodOperationName(ContainerRequestContext)}.
   *
   * @return operation name
   */
  @Override
  protected String getOperationName(ContainerRequestContext containerRequestContext) {
    CacheKey key =
        CacheKey.of(resourceInfo.getResourceMethod(), containerRequestContext.getMethod());
    String operationName = methodToPathCache.get(key);
    if (operationName != null) {
      return operationName;
    }

    operationName = getPathOperationName(containerRequestContext);

    if (operationName.isEmpty()) {
      operationName = getResourceMethodOperationName(containerRequestContext);
    }

    methodToPathCache.put(key, operationName);
    return operationName;
  }

  /**
   * Retrieves an operation name that uses the resource method name of the matched jersey resource
   *
   * @return the operation name
   */
  private String getResourceMethodOperationName(ContainerRequestContext containerRequestContext) {
    Method method = resourceInfo.getResourceMethod();
    return String.format(
        "%s:%s:%s",
        containerRequestContext.getMethod(),
        method.getDeclaringClass().getCanonicalName(),
        method.getName());
  }

  /**
   * Retrieves an operation name that contains the http method and value of the {@link Path} used by
   * a Jersey server implementation.
   *
   * @return the operation name
   */
  private String getPathOperationName(ContainerRequestContext containerRequestContext) {
    String operationName = "";
    try {
      if (normalizedTemplate != null && containerRequestContext instanceof ContainerRequest) {
        ContainerRequest containerRequest = (ContainerRequest) containerRequestContext;
        ExtendedUriInfo uriInfo = containerRequest.getUriInfo();
        if (uriInfo instanceof UriRoutingContext) {
          UriRoutingContext uriRoutingContext = (UriRoutingContext) uriInfo;
          List<UriTemplate> templates = uriRoutingContext.getMatchedTemplates();
          String path = "";
          for (UriTemplate uriTemplate : templates) {
            String template = (String) normalizedTemplate.get(uriTemplate);
            path = template + path;
          }
          operationName = String.format("%s:%s", containerRequest.getMethod(), path);
        }
      }
    } catch (Exception e) {
      log.error("Unable to get operation name", e);
    }

    return operationName;
  }

  @VisibleForTesting
  Map<CacheKey, String> getCache() {
    return new ConcurrentHashMap<CacheKey, String>(methodToPathCache);
  }

  @Value(staticConstructor = "of")
  static class CacheKey {
    private final Method resourceMethod;
    private final String httpMethod;
  }
}
