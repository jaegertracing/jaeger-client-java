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
import org.apache.thrift.protocol.TBinaryProtocol.Factory;
import org.apache.thrift.protocol.TProtocolFactory;

public class HttpSender extends ThriftSender {
  private static final String HTTP_COLLECTOR_JAEGER_THRIFT_FORMAT_PARAM = "format=jaeger.thrift";
  private static final TProtocolFactory PROTOCOL_FACTORY = new Factory();
  private static final TSerializer SERIALIZER = new TSerializer(PROTOCOL_FACTORY);

  private final OkHttpClient httpClient = new OkHttpClient();
  private final HttpUrl collectorUrl;
  private static final int ONE_MB_IN_BYTES = 1048576;

  /**
   * @param endpoint Jaeger REST endpoint consuming jaeger.thrift, e.g
   * http://localhost:14268/api/traces
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
    this(HttpUrl.parse(String.format("%s?%s", endpoint, HTTP_COLLECTOR_JAEGER_THRIFT_FORMAT_PARAM)), maxPayloadBytes);
  }

  /**
   * @param url Jaeger url accepting jaeger.thrift. This needs to include query params, etc.
   * @param maxPayloadBytes max bytes to serialize as payload
   */
  public HttpSender(HttpUrl url, int maxPayloadBytes) {
    super(PROTOCOL_FACTORY, maxPayloadBytes);
    if (url == null) {
      throw new IllegalArgumentException("Could not parse url.");
    }
    this.collectorUrl = url;
  }

  @Override
  public void send(Process process, List<Span> spans) throws TException {
    Batch batch = new Batch(process, spans);
    byte[] bytes = SERIALIZER.serialize(batch);

    RequestBody body = RequestBody.create(MediaType.parse("application/x-thrift"), bytes);
    Request request = new Request.Builder().url(collectorUrl).post(body).build();
    try {
      Response response = httpClient.newCall(request).execute();
      if (!response.isSuccessful()) {
        String responseBody = response.body() != null ? response.body().string() : "null";
        String exceptionMessage = String.format("Could not send %d spans, response %d: %s",
                                                spans.size(), response.code(), responseBody);
        throw new TException(exceptionMessage);
      }
    } catch (IOException e) {
      throw new TException("Could not send " + spans.size() + ", spans", e);
    }
  }
}
