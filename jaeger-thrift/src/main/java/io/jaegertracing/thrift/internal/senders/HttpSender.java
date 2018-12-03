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

package io.jaegertracing.thrift.internal.senders;

import io.jaegertracing.internal.exceptions.SenderException;
import io.jaegertracing.thriftjava.Batch;
import io.jaegertracing.thriftjava.Process;
import io.jaegertracing.thriftjava.Span;
import java.io.IOException;
import java.util.List;
import lombok.ToString;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@ToString
public class HttpSender extends ThriftSender {
  private static final String HTTP_COLLECTOR_JAEGER_THRIFT_FORMAT_PARAM = "format=jaeger.thrift";
  private static final int ONE_MB_IN_BYTES = 1048576;
  private static final MediaType MEDIA_TYPE_THRIFT = MediaType.parse("application/x-thrift");

  @ToString.Exclude private final OkHttpClient httpClient;
  @ToString.Exclude private final Request.Builder requestBuilder;

  protected HttpSender(Builder builder) {
    super(ProtocolType.Binary, builder.maxPacketSize);
    HttpUrl collectorUrl = HttpUrl
        .parse(String.format("%s?%s", builder.endpoint, HTTP_COLLECTOR_JAEGER_THRIFT_FORMAT_PARAM));
    if (collectorUrl == null) {
      throw new IllegalArgumentException("Could not parse url.");
    }
    this.httpClient = builder.clientBuilder.build();
    this.requestBuilder = new Request.Builder().url(collectorUrl);
  }

  @Override
  public void send(Process process, List<Span> spans) throws SenderException {
    Batch batch = new Batch(process, spans);
    byte[] bytes = null;
    try {
      bytes = serialize(batch);
    } catch (Exception e) {
      throw new SenderException(String.format("Failed to serialize %d spans", spans.size()), e, spans.size());
    }

    RequestBody body = RequestBody.create(MEDIA_TYPE_THRIFT, bytes);
    Request request = requestBuilder.post(body).build();
    Response response;
    try {
      response = httpClient.newCall(request).execute();
    } catch (IOException e) {
      throw new SenderException(String.format("Could not send %d spans", spans.size()), e, spans.size());
    }

    if (response.isSuccessful()) {
      response.close();
      return;
    }

    String responseBody;
    try {
      responseBody = response.body() != null ? response.body().string() : "null";
    } catch (IOException e) {
      responseBody = "unable to read response";
    }

    String exceptionMessage = String.format("Could not send %d spans, response %d: %s",
        spans.size(), response.code(), responseBody);
    throw new SenderException(exceptionMessage, null, spans.size());
  }

  public static class Builder {
    private final String endpoint;
    private int maxPacketSize = ONE_MB_IN_BYTES;
    private Interceptor authInterceptor;
    private OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

    /**
     * @param endpoint jaeger-collector HTTP endpoint e.g. http://localhost:14268/api/traces
     */
    public Builder(String endpoint) {
      this.endpoint = endpoint;
    }

    public Builder withClient(OkHttpClient client) {
      this.clientBuilder = client.newBuilder();
      return this;
    }

    public Builder withMaxPacketSize(int maxPacketSizeBytes) {
      this.maxPacketSize = maxPacketSizeBytes;
      return this;
    }

    public Builder withAuth(String username, String password) {
      this.authInterceptor = getAuthInterceptor(Credentials.basic(username, password));
      return this;
    }

    public Builder withAuth(String authToken) {
      this.authInterceptor = getAuthInterceptor("Bearer " + authToken);
      return this;
    }

    public HttpSender build() {
      if (authInterceptor != null) {
        clientBuilder.addInterceptor(authInterceptor);
      }
      return new HttpSender(this);
    }

    private Interceptor getAuthInterceptor(final String headerValue) {
      return new Interceptor() {
        @Override
        public Response intercept(Chain chain) throws IOException {
          return chain.proceed(
              chain.request()
                  .newBuilder()
                  .addHeader("Authorization", headerValue)
                  .build()
          );
        }
      };
    }
  }
}
