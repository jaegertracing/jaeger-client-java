/*
 * Copyright (c) 2020, The Jaeger Authors
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

import io.jaegertracing.Configuration;
import io.jaegertracing.spi.Sender;
import io.jaegertracing.spi.SenderFactory;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Factory for {@link Sender} instances backed by Thrift. The actual sender implementation can be either
 * {@link HttpSender} or {@link UdpSender}, based on the given {@link Configuration.SenderConfiguration}.
 */
@Slf4j
@ToString
public class ThriftSenderFactory implements SenderFactory {
  @Override
  public Sender getSender(Configuration.SenderConfiguration conf) {
    if (null != conf.getEndpoint() && !conf.getEndpoint().isEmpty()) {
      HttpSender.Builder httpSenderBuilder = new HttpSender.Builder(conf.getEndpoint());
      if (null != conf.getAuthUsername() && !conf.getAuthUsername().isEmpty()
          && null != conf.getAuthPassword() && !conf.getAuthPassword().isEmpty()) {
        log.debug("Using HTTP Basic authentication with data from the environment variables.");
        httpSenderBuilder.withAuth(conf.getAuthUsername(), conf.getAuthPassword());
      } else if (null != conf.getAuthToken() && !conf.getAuthToken().isEmpty()) {
        log.debug("Auth Token environment variable found.");
        httpSenderBuilder.withAuth(conf.getAuthToken());
      }

      log.debug("Using the HTTP Sender to send spans directly to the endpoint.");
      return httpSenderBuilder.build();
    }

    log.debug("Using the UDP Sender to send spans to the agent.");
    return new UdpSender(
        stringOrDefault(conf.getAgentHost(), UdpSender.DEFAULT_AGENT_UDP_HOST),
        numberOrDefault(conf.getAgentPort(), UdpSender.DEFAULT_AGENT_UDP_COMPACT_PORT).intValue(),
        0 /* max packet size */);
  }

  @Override
  public String getType() {
    return "thrift";
  }

  private static String stringOrDefault(String value, String defaultValue) {
    return value != null && value.length() > 0 ? value : defaultValue;
  }

  private static Number numberOrDefault(Number value, Number defaultValue) {
    return value != null ? value : defaultValue;
  }
}
