/*
 * Copyright (c) 2017, Uber Technologies, Inc
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

package com.uber.jaeger.baggage;

import static com.uber.jaeger.utils.Utils.makeGetRequest;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.uber.jaeger.baggage.http.BaggageRestriction;
import com.uber.jaeger.exceptions.BaggageRestrictionException;

import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.utils.URIBuilder;

public class HttpBaggageRestrictionProxy implements BaggageRestrictionProxy {
  private static final String DEFAULT_HOST_PORT = "localhost:5778";
  private final URIBuilder builder;
  private final Gson gson = new Gson();

  public HttpBaggageRestrictionProxy(String hostPort) throws URISyntaxException {
    hostPort = hostPort != null ? hostPort : DEFAULT_HOST_PORT;
    this.builder = new URIBuilder("http://" + hostPort).setPath("/baggageRestrictions");
  }

  List<BaggageRestriction> parseJson(String json) throws BaggageRestrictionException {
    try {
      Type listType = new TypeToken<ArrayList<BaggageRestriction>>(){}.getType();
      return gson.fromJson(json, listType);
    } catch (JsonSyntaxException e) {
      throw new BaggageRestrictionException("Cannot deserialize json", e);
    }
  }

  @Override
  public List<BaggageRestriction> getBaggageRestrictions(String serviceName)
      throws BaggageRestrictionException {
    String jsonString;
    try {
      jsonString =
          makeGetRequest(builder.setParameter("service", serviceName).build());
    } catch (Exception e) {
      throw new BaggageRestrictionException(
          "http call to get baggage restriction from local agent failed.", e);
    }
    return parseJson(jsonString);
  }
}
