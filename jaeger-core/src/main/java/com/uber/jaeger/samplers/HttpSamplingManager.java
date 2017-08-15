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

package com.uber.jaeger.samplers;

import static com.uber.jaeger.utils.Utils.makeGetRequest;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.uber.jaeger.exceptions.SamplingStrategyErrorException;
import com.uber.jaeger.samplers.http.SamplingStrategyResponse;

import java.io.IOException;
import java.net.URLEncoder;

import lombok.ToString;


@ToString
public class HttpSamplingManager implements SamplingManager {
  private static final String DEFAULT_HOST_PORT = "localhost:5778";
  private final Gson gson = new Gson();
  private final String hostPort;

  public HttpSamplingManager(String hostPort) {
    this.hostPort = hostPort != null ? hostPort : DEFAULT_HOST_PORT;
  }

  SamplingStrategyResponse parseJson(String json) {
    try {
      return gson.fromJson(json, SamplingStrategyResponse.class);
    } catch (JsonSyntaxException e) {
      throw new SamplingStrategyErrorException("Cannot deserialize json", e);
    }
  }

  @Override
  public SamplingStrategyResponse getSamplingStrategy(String serviceName)
      throws SamplingStrategyErrorException {
    String jsonString;
    try {
      jsonString =
          makeGetRequest(
              "http://" + hostPort + "/?service=" + URLEncoder.encode(serviceName, "UTF-8"));
    } catch (IOException e) {
      throw new SamplingStrategyErrorException(
          "http call to get sampling strategy from local agent failed.", e);
    }

    return parseJson(jsonString);
  }
}
