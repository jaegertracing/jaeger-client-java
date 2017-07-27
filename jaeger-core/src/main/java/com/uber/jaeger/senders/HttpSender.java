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
import org.apache.thrift.protocol.TProtocolFactory;

public class HttpSender extends ThriftSender {

  private static final String HTTP_COLLECTOR_JAEGER_THRIFT_FORMAT_PARAM = "format=jaeger.thrift";
  private static final TProtocolFactory PROTOCOL_FACTORY = new TBinaryProtocol.Factory();
  private static final TSerializer SERIALIZER = new TSerializer(PROTOCOL_FACTORY);
  private static final int ONE_MB_IN_BYTES = 1048576;
  private static final MediaType MEDIA_TYPE_THRIFT = MediaType.parse("application/x-thrift");
  private final OkHttpClient httpClient;
  private final HttpUrl collectorUrl;

  /**
   * @param endpoint Jaeger REST endpoint consuming jaeger.thrift, e.g
   * http://localhost:14268/api/traces
   *
   * Uses the default {@link okhttp3.OkHttpClient} which uses {@link okhttp3.ConnectionPool#ConnectionPool()}.
   * Use {@link HttpSender#HttpSender(java.lang.String, int, okhttp3.OkHttpClient)} to adjust parameters.
   */
  public HttpSender(String endpoint) {
    this(endpoint, ONE_MB_IN_BYTES, new OkHttpClient());
  }

  /**
   * @param endpoint Jaeger REST endpoint consuming jaeger.thrift, e.g
   * http://localhost:14268/api/traces
   * @param maxPayloadBytes max bytes to serialize as payload
   * @param client a client used to make http requests
   */
  public HttpSender(String endpoint, int maxPayloadBytes, OkHttpClient client) {
    super(PROTOCOL_FACTORY, maxPayloadBytes);
    this.collectorUrl = HttpUrl.parse(String.format("%s?%s", endpoint, HTTP_COLLECTOR_JAEGER_THRIFT_FORMAT_PARAM));
    if (this.collectorUrl == null) {
      throw new IllegalArgumentException("Could not parse url.");
    }
    this.httpClient = client;
  }

  @Override
  public void send(Process process, List<Span> spans) throws TException {
    Batch batch = new Batch(process, spans);
    byte[] bytes = SERIALIZER.serialize(batch);

    RequestBody body = RequestBody.create(MEDIA_TYPE_THRIFT, bytes);
    Request request = new Request.Builder().url(collectorUrl).post(body).build();
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
