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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.jaeger.exceptions.SamplingStrategyErrorException;
import com.uber.jaeger.thrift.sampling_manager.ProbabilisticSamplingStrategy;
import com.uber.jaeger.thrift.sampling_manager.RateLimitingSamplingStrategy;
import com.uber.jaeger.thrift.sampling_manager.SamplingStrategyResponse;
import com.uber.jaeger.thrift.sampling_manager.SamplingStrategyType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class HTTPSamplingManager implements SamplingManager {
  private static final String defaultSamplingServerHostPort = "localhost:5778";
  private String hostPort = defaultSamplingServerHostPort;

  public HTTPSamplingManager(String hostPort) {
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
      try (BufferedReader rd =
          new BufferedReader(
              new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = rd.readLine()) != null) {
          result.append(line);
        }
      }
    } finally {
      conn.disconnect();
    }
    return result.toString();
  }

  private SamplingStrategyResponse jsonStringToSamplingStrategy(String jsonString) {
    HashMap<String, Object> result;
    try {
      result = new ObjectMapper().readValue(jsonString, HashMap.class);
    } catch (IOException e) {
      throw new SamplingStrategyErrorException(
          "Invalid json received when fetching sampling strategy from local agent.", e);
    }

    SamplingStrategyType samplingType =
        SamplingStrategyType.findByValue((Integer) result.get("strategyType"));
    if (samplingType == SamplingStrategyType.PROBABILISTIC) {
      Double samplingRate;
      try {
        samplingRate =
            (Double)
                ((HashMap<String, Object>) result.get("probabilisticSampling")).get("samplingRate");
      } catch (Exception e) {
        throw new SamplingStrategyErrorException(
            String.format(
                "Unexpected format %s for parsing probabilistic sampling strategy", jsonString));
      }

      return new SamplingStrategyResponse(samplingType)
          .setProbabilisticSampling(new ProbabilisticSamplingStrategy(samplingRate));
    }

    if (samplingType == SamplingStrategyType.RATE_LIMITING) {
      int maxTracesPerSecond;
      try {
        maxTracesPerSecond =
            (Integer)
                ((HashMap<String, Object>) result.get("rateLimitingSampling"))
                    .get("maxTracesPerSecond");
      } catch (Exception e) {
        throw new SamplingStrategyErrorException(
            String.format(
                "Unexpected format %s for parsing rateLimiting sampling strategy", jsonString));
      }

      return new SamplingStrategyResponse(samplingType)
          .setRateLimitingSampling(new RateLimitingSamplingStrategy((short) maxTracesPerSecond));
    }

    throw new SamplingStrategyErrorException("Unrecognized sampling strategy type.");
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

    return jsonStringToSamplingStrategy(jsonString);
  }

  @Override
  public String toString() {
    return "{\"_class\":\"HTTPSamplingManager\", "
        + "\"hostPort\":"
        + (hostPort == null ? "null" : "\"" + hostPort + "\"")
        + "}";
  }
}
