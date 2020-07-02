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

import io.jaegertracing.internal.exceptions.SenderException;
import org.junit.Test;

import java.net.SocketException;
import java.util.Collections;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UdpLazinessTest {
    @Test
    public void testLazyInitializationOfAgent() {
        UdpSender udpSender = new UdpSender("agent.acme.test", 55555, 0);

        try {
            udpSender.send(null, Collections.emptyList());
            fail("Send should fail!");
        } catch (SenderException e) {
            assertTrue("send should throw a socket exception", e.getCause().getCause() instanceof SocketException);
        }
    }
}
