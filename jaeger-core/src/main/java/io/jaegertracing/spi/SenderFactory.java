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

package io.jaegertracing.spi;

import io.jaegertracing.Configuration;
import io.jaegertracing.internal.senders.NoopSender;
import io.jaegertracing.internal.senders.SenderResolver;

/**
 * Represents a class that knows how to select and build the appropriate {@link Sender} based on the given
 * {@link Configuration.SenderConfiguration}. This factory is usually used in conjunction with the
 * {@link SenderResolver}, so that the appropriate factory will be loaded via
 * {@link java.util.ServiceLoader}.
 */
public interface SenderFactory {
  /**
   * Builds and/or selects the appropriate sender based on the given {@link Configuration.SenderConfiguration}
   * @param senderConfiguration the sender configuration
   * @return an appropriate sender based on the configuration, or {@link NoopSender}.
   */
  Sender getSender(Configuration.SenderConfiguration senderConfiguration);

  /**
   * The Factory's name. Can be specified via {@link Configuration#JAEGER_SENDER_FACTORY} to disambiguate
   * the resolution, in case multiple senders are available via the service loader.
   * @return a simple factory name
   */
  String getType();
}
