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

/**
 * Represents a class that knows how to select and build the appropriate {@link Sender} based on the given
 * {@link Configuration.SenderConfiguration}
 */
public interface SenderFactory {
  /**
   * Builds and/or selects the appropriate sender based on the given {@link Configuration.SenderConfiguration}
   * @param senderConfiguration the sender configuration
   * @return an appropriate sender based on the configuration, or {@link NoopSender}.
   */
  Sender getSender(Configuration.SenderConfiguration senderConfiguration);
}
