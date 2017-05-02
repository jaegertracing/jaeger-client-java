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

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.uber.jaeger.exceptions.SamplingStrategyErrorException;
import com.uber.jaeger.samplers.http.SamplingStrategyResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import lombok.ToString;

@ToString
public class HttpSamplingManager implements SamplingManager {
  private static final String defaultSamplingServerHostPort = "localhost:5778";
  private String hostPort = defaultSamplingServerHostPort;
  private Gson gson = new Gson();

  public HttpSamplingManager(String hostPort) {
    if (hostPort != null) {
      this.hostPort = hostPort;
    }
  }

  private String makeGetRequest(String urlToRead) throws IOException {
    URL url = new URL(urlToRead);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    StringBuilder result = new StringBuilder();
    try {
      conn.setRequestMethod("GET");
      BufferedReader rd =
          new BufferedReader(
              new InputStreamReader(conn.getInputStream(), Charset.forName("UTF-8")));
      try {
        String line;
        while ((line = rd.readLine()) != null) {
          result.append(line);
        }
      } finally {
        rd.close();
      }
    } finally {
      conn.disconnect();
    }
    return result.toString();
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
