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

package io.jaegertracing.senders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.jaegertracing.Configuration;
import io.jaegertracing.Span;
import io.jaegertracing.exceptions.SenderException;
import org.junit.After;
import org.junit.Test;

public class SenderResolverTest {

  @After
  public void clear() {
    SenderFactoryToBeLoaded.sender = new NoopSender();
    System.clearProperty(Configuration.JAEGER_SENDER_FACTORY);
  }

  @Test
  public void testBasedOnEnvVar() {
    System.setProperty(Configuration.JAEGER_SENDER_FACTORY, InMemorySenderFactory.class.getName());
    Sender sender = SenderResolver.resolve();
    assertTrue("Expected to have sender as instance of InMemorySender, but was " + sender.getClass(),
        sender instanceof InMemorySender);
  }

  @Test
  public void testFallbackToNoopSender() {
    System.setProperty(Configuration.JAEGER_SENDER_FACTORY, "non-existing-sender-factory");
    Sender sender = SenderResolver.resolve();
    assertTrue("Expected to have sender as instance of NoopSender, but was " + sender.getClass(),
        sender instanceof NoopSender);
  }

  @Test
  public void testFromServiceLoader() {
    CustomSender customSender = new CustomSender();
    SenderFactoryToBeLoaded.sender = customSender;
    Sender sender = SenderResolver.resolve();
    assertEquals(customSender, sender);
  }

  static class CustomSender implements Sender {

    @Override
    public int append(Span span) throws SenderException {
      return 0;
    }

    @Override
    public int flush() throws SenderException {
      return 0;
    }

    @Override
    public int close() throws SenderException {
      return 0;
    }

    @Override
    public String toString() {
      return "CustomSender{}";
    }
  }
}
