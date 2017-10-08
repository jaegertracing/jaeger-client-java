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
package com.uber.jaeger.filters.jaxrs2;

import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MultivaluedMap;

public class ServerRequestCarrier implements TextMap {
  private final ContainerRequestContext requestContext;

  public ServerRequestCarrier(ContainerRequestContext requestContext) {
    this.requestContext = requestContext;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    MultivaluedMap<String, String> headers = requestContext.getHeaders();
    final Iterator<Map.Entry<String, List<String>>> iterator = headers.entrySet().iterator();

    return new Iterator<Map.Entry<String, String>>() {
      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public Map.Entry<String, String> next() {
        Map.Entry<String, List<String>> next = iterator.next();
        String key = next.getKey();
        String value = next.getValue().get(0);
        return new ImmutableMapEntry(key, value);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  @Override
  public void put(String key, String value) {
    throw new UnsupportedOperationException();
  }

  public static final class ImmutableMapEntry implements Map.Entry<String, String> {
    private final String key;
    private final String value;

    public ImmutableMapEntry(String key, String value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public String getKey() {
      return key;
    }

    @Override
    public String getValue() {
      return value;
    }

    @Override
    public String setValue(String value) {
      throw new UnsupportedOperationException();
    }
  }
}
