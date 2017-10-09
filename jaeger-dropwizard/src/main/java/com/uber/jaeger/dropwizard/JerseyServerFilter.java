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
