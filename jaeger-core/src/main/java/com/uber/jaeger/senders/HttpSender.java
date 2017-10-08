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
package com.uber.jaeger.senders;

import com.uber.jaeger.thriftjava.Batch;
import com.uber.jaeger.thriftjava.Process;
import com.uber.jaeger.thriftjava.Span;
import java.io.IOException;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;

public class HttpSender extends ThriftSender {

  private static final String HTTP_COLLECTOR_JAEGER_THRIFT_FORMAT_PARAM = "format=jaeger.thrift";
  private static final int ONE_MB_IN_BYTES = 1048576;
  private static final MediaType MEDIA_TYPE_THRIFT = MediaType.parse("application/x-thrift");
  private final TSerializer serializer;
  private final OkHttpClient httpClient;
  private final Request.Builder requestBuilder;

  /**
   * @param endpoint Jaeger REST endpoint consuming jaeger.thrift, e.g
   * http://localhost:14268/api/traces
   *
   * Uses the default {@link okhttp3.OkHttpClient} which uses {@link okhttp3.ConnectionPool#ConnectionPool()}.
   * Use {@link HttpSender#HttpSender(java.lang.String, int, okhttp3.OkHttpClient)} to adjust parameters.
   */
  public HttpSender(String endpoint) {
    this(endpoint, ONE_MB_IN_BYTES);
  }

  /**
   * @param endpoint Jaeger REST endpoint consuming jaeger.thrift, e.g
   * http://localhost:14268/api/traces
   * @param maxPayloadBytes max bytes to serialize as payload
   */
  public HttpSender(String endpoint, int maxPayloadBytes) {
    this(endpoint, maxPayloadBytes, new OkHttpClient());
  }

  /**
   * @param endpoint Jaeger REST endpoint consuming jaeger.thrift, e.g
   * http://localhost:14268/api/traces
   * @param maxPayloadBytes max bytes to serialize as payload
   * @param client a client used to make http requests
   */
  private HttpSender(String endpoint, int maxPayloadBytes, OkHttpClient client) {
    super(new TBinaryProtocol.Factory(), maxPayloadBytes);
    HttpUrl collectorUrl = HttpUrl
        .parse(String.format("%s?%s", endpoint, HTTP_COLLECTOR_JAEGER_THRIFT_FORMAT_PARAM));
    if (collectorUrl == null) {
      throw new IllegalArgumentException("Could not parse url.");
    }
    this.httpClient = client;
    this.requestBuilder = new Request.Builder().url(collectorUrl);
    this.serializer = new TSerializer(protocolFactory);
  }

  @Override
  public void send(Process process, List<Span> spans) throws TException {
    Batch batch = new Batch(process, spans);
    byte[] bytes = serializer.serialize(batch);

    RequestBody body = RequestBody.create(MEDIA_TYPE_THRIFT, bytes);
    Request request = requestBuilder.post(body).build();
    Response response;
    try {
      response = httpClient.newCall(request).execute();
    } catch (IOException e) {
      throw new TException(String.format("Could not send %d spans", spans.size()), e);
    }

    if (!response.isSuccessful()) {
      String responseBody;
      try {
        responseBody = response.body() != null ? response.body().string() : "null";
      } catch (IOException e) {
        responseBody = "unable to read response";
      }

      String exceptionMessage = String.format("Could not send %d spans, response %d: %s",
          spans.size(), response.code(), responseBody);
      throw new TException(exceptionMessage);
    }
  }
}
