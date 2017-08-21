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

import static com.uber.jaeger.utils.Http.makeGetRequest;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.uber.jaeger.baggage.http.BaggageRestrictionResponse;
import com.uber.jaeger.exceptions.BaggageRestrictionManagerException;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class HttpBaggageRestrictionManagerProxy implements BaggageRestrictionManagerProxy {
  private static final String DEFAULT_HOST_PORT = "localhost:5778";
  private final Gson gson = new Gson();
  private final String hostPort;

  public HttpBaggageRestrictionManagerProxy(String hostPort) {
    this.hostPort = hostPort != null ? hostPort : DEFAULT_HOST_PORT;
  }

  List<BaggageRestrictionResponse> parseJson(String json) throws BaggageRestrictionManagerException {
    try {
      Type listType = new TypeToken<ArrayList<BaggageRestrictionResponse>>(){}.getType();
      return gson.fromJson(json, listType);
    } catch (JsonSyntaxException e) {
      throw new BaggageRestrictionManagerException("Cannot deserialize json", e);
    }
  }

  @Override
  public List<BaggageRestrictionResponse> getBaggageRestrictions(String serviceName)
      throws BaggageRestrictionManagerException {
    String jsonString;
    try {
      jsonString =
          makeGetRequest(
              "http://" + hostPort + "/baggageRestrictions?service=" + URLEncoder.encode(serviceName, "UTF-8"));
    } catch (IOException e) {
      throw new BaggageRestrictionManagerException(
          "http call to get baggage restriction from local agent failed.", e);
    }
    return parseJson(jsonString);
  }
}
