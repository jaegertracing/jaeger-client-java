/*
 * Copyright (c) 2017, Uber Technologies, Inc
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

package io.jaegertracing.internal.baggage;

import static io.jaegertracing.internal.utils.Http.makeGetRequest;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.jaegertracing.internal.baggage.http.BaggageRestrictionResponse;
import io.jaegertracing.internal.exceptions.BaggageRestrictionManagerException;
import io.jaegertracing.spi.BaggageRestrictionManagerProxy;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class HttpBaggageRestrictionManagerProxy implements BaggageRestrictionManagerProxy {
  private static final String DEFAULT_HOST_PORT = "localhost:5778";
  private static final Type LIST_TYPE = new TypeToken<ArrayList<BaggageRestrictionResponse>>(){}.getType();
  private final Gson gson = new Gson();
  private final String hostPort;

  public HttpBaggageRestrictionManagerProxy(String hostPort) {
    this.hostPort = hostPort != null ? hostPort : DEFAULT_HOST_PORT;
  }

  List<BaggageRestrictionResponse> parseJson(String json) throws BaggageRestrictionManagerException {
    try {
      return gson.fromJson(json, LIST_TYPE);
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
