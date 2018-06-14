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

import io.jaegertracing.Configuration;
import java.util.Iterator;
import java.util.ServiceLoader;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides a way to resolve an appropriate {@link Sender}
 */
@Slf4j
public class SenderResolver {

  /**
   * Resolves a {@link Sender} by passing {@link Configuration.SenderConfiguration#fromEnv()} down to the
   * {@link SenderFactory}
   *
   * @see #resolve(Configuration.SenderConfiguration)
   * @return the resolved Sender, or NoopSender
   */
  public static Sender resolve() {
    return resolve(Configuration.SenderConfiguration.fromEnv());
  }

  /**
   * Resolves a sender by passing the given {@link Configuration.SenderConfiguration} down to the
   * {@link SenderFactory}. The factory is loaded either based on the value from the environment variable
   * {@link Configuration#JAEGER_SENDER_FACTORY} or, in its absence or failure to deliver a {@link Sender},
   * via the {@link ServiceLoader}. If no factories are found, a {@link NoopSender} is returned. If multiple factories
   * are available, the factory whose {@link SenderFactory#getType()} matches the JAEGER_SENDER_FACTORY env var is
   * selected. If none matches, {@link NoopSender} is returned.
   *
   * @param senderConfiguration the configuration to pass down to the factory
   * @return the resolved Sender, or NoopSender
   */
  public static Sender resolve(Configuration.SenderConfiguration senderConfiguration) {
    String senderFactoryClass = System.getProperty(Configuration.JAEGER_SENDER_FACTORY);

    if (senderFactoryClass == null || senderFactoryClass.isEmpty()) {
      return loadViaServiceLoader(senderConfiguration);
    }

    try {
      Object factoryObject = Class.forName(senderFactoryClass).getDeclaredConstructor().newInstance();
      if (factoryObject instanceof SenderFactory) {
        return ((SenderFactory) factoryObject).getSender(senderConfiguration);
      } else {
        log.warn(String.format("The class specified via JAEGER_SENDER_FACTORY (%s) should be a SenderFactory.",
            senderFactoryClass));
      }
    } catch (ClassNotFoundException e) {
      log.info("The property JAEGER_SENDER_FACTORY doesn't seem to relate to an existing class. Skipping.");
    } catch (Exception e) {
      log.warn(String.format("Exception while trying to get a sender from the factory %s. Falling back to loading via "
          + "the service loader", senderFactoryClass), e);
    }

    return loadViaServiceLoader(senderConfiguration);
  }

  private static Sender loadViaServiceLoader(Configuration.SenderConfiguration senderConfiguration) {
    ServiceLoader<SenderFactory> senderFactoryServiceLoader = ServiceLoader.load(SenderFactory.class);
    Iterator<SenderFactory> senderFactoryIterator = senderFactoryServiceLoader.iterator();

    boolean hasMultipleFactories = false;
    if (senderFactoryIterator.hasNext()) {
      SenderFactory senderFactory = senderFactoryIterator.next();

      if (senderFactoryIterator.hasNext()) {
        log.debug("There are multiple factories available via the service loader.");
        hasMultipleFactories = true;
      }

      if (hasMultipleFactories) {
        // we compare the factory name with JAEGER_SENDER_FACTORY, as a way to know which
        // factory the user wants:
        String requestedFactory = System.getProperty(Configuration.JAEGER_SENDER_FACTORY);
        if (senderFactory.getType().equals(requestedFactory)) {
          log.info(
              String.format("Found the requested (%s) sender factory: %s",
                  requestedFactory,
                  senderFactory)
          );
          Sender sender = senderFactory.getSender(senderConfiguration);
          log.info(String.format("Using sender %s", sender));
          return sender;
        }
      } else {
        log.info(String.format("Found a sender factory: %s", senderFactory));
        Sender sender = senderFactory.getSender(senderConfiguration);
        log.info(String.format("Using sender %s", sender));
        return sender;
      }
    }

    log.warn("No suitable sender found. Using NoopSender, meaning that data will not be sent anywhere!");
    return new NoopSender();
  }
}
