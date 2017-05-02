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
