/*
 * Copyright (c) 2018, The Jaeger Authors
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

import static org.junit.Assert.assertTrue;

import io.jaegertracing.Configuration;
import io.jaegertracing.spi.Sender;
import org.junit.Before;
import org.junit.Test;

public class ThriftSenderFactoryTest {

  @Before
  public void setup() {
    System.clearProperty(Configuration.JAEGER_ENDPOINT);
    System.clearProperty(Configuration.JAEGER_AUTH_TOKEN);
    System.clearProperty(Configuration.JAEGER_USER);
    System.clearProperty(Configuration.JAEGER_PASSWORD);

    System.clearProperty(Configuration.JAEGER_AGENT_HOST);
    System.clearProperty(Configuration.JAEGER_AGENT_PORT);
  }

  @Test
  public void testSenderWithEndpointWithoutAuthData() {
    System.setProperty(Configuration.JAEGER_ENDPOINT, "https://jaeger-collector:14268/api/traces");
    Sender sender = Configuration.SenderConfiguration.fromEnv().getSender();
    assertTrue(sender instanceof HttpSender);
  }

  @Test
  public void testSenderWithAgentDataFromEnv() {
    System.setProperty(Configuration.JAEGER_AGENT_HOST, "jaeger-agent");
    System.setProperty(Configuration.JAEGER_AGENT_PORT, "6832");
    Sender sender = Configuration.SenderConfiguration.fromEnv().getSender();
    assertTrue(sender instanceof UdpSender);
  }

  @Test
  public void testSenderWithBasicAuthUsesHttpSender() {
    Configuration.SenderConfiguration senderConfiguration = new Configuration.SenderConfiguration()
        .withEndpoint("https://jaeger-collector:14268/api/traces")
        .withAuthUsername("username")
        .withAuthPassword("password");
    assertTrue(senderConfiguration.getSender() instanceof HttpSender);
  }

  @Test
  public void testSenderWithAuthTokenUsesHttpSender() {
    Configuration.SenderConfiguration senderConfiguration = new Configuration.SenderConfiguration()
        .withEndpoint("https://jaeger-collector:14268/api/traces")
        .withAuthToken("authToken");
    assertTrue(senderConfiguration.getSender() instanceof HttpSender);
  }

  @Test
  public void testSenderWithAllPropertiesReturnsHttpSender() {
    System.setProperty(Configuration.JAEGER_ENDPOINT, "https://jaeger-collector:14268/api/traces");
    System.setProperty(Configuration.JAEGER_AGENT_HOST, "jaeger-agent");
    System.setProperty(Configuration.JAEGER_AGENT_PORT, "6832");

    assertTrue(Configuration.SenderConfiguration.fromEnv().getSender() instanceof HttpSender);
  }

  @Test
  public void testDefaultConfigurationReturnsUdpSender() {
    assertTrue(Configuration.SenderConfiguration.fromEnv().getSender() instanceof UdpSender);
  }

}
