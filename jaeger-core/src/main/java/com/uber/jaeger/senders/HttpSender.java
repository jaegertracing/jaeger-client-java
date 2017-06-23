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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol.Factory;
import org.apache.thrift.protocol.TProtocolFactory;

public class HttpSender extends ThriftSender {
  private static final String HTTP_COLLECTOR_JAEGER_THRIFT_FORMAT_PARAM = "format=jaeger.thrift";
  private static final TProtocolFactory PROTOCOL_FACTORY = new Factory();
  private static final TSerializer SERIALIZER = new TSerializer(PROTOCOL_FACTORY);

  private final HttpClient httpClient = new DefaultHttpClient();
  private final URI collectorUri;

  /**
   * @param endpoint Jaeger REST endpoint consuming jaeger.thrift, e.g http://localhost:14268/api/traces
   * @param maxPacketSize max packet size
   */
  public HttpSender(String endpoint, int maxPacketSize) {
    super(PROTOCOL_FACTORY, maxPacketSize);
    this.collectorUri = constructCollectorUri(endpoint);
  }

  @Override
  public void send(Process process, List<com.uber.jaeger.thriftjava.Span> spans) throws TException {
    Batch batch = new Batch(process, spans);
    byte[] bytes = SERIALIZER.serialize(batch);

    try {
      HttpPost httpPost = new HttpPost(this.collectorUri);
      httpPost.setEntity(new ByteArrayEntity(bytes));
      HttpResponse response = httpClient.execute(httpPost);
      if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
        throw new TException("Could not send " + spans.size() + " spans, response "
            + response.getStatusLine().getStatusCode());
      }
    } catch (IOException e) {
      throw new TException("Could not send " + spans.size() + ", spans", e);
    }
  }

  private URI constructCollectorUri(String endpoint) {
    try {
      return new URI(String.format("%s?%s", endpoint, HTTP_COLLECTOR_JAEGER_THRIFT_FORMAT_PARAM));
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Wrong collector host", e);
    }
  }
}
