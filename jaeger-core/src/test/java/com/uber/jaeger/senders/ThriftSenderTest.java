/*
 * Copyright (c) 2017-2018, The Jaeger Authors
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

package com.uber.jaeger.senders;

import com.uber.jaeger.exceptions.SenderException;
import com.uber.jaeger.thrift.senders.ThriftSenderBase.ProtocolType;
import com.uber.jaeger.thriftjava.Process;
import com.uber.jaeger.thriftjava.Span;

import java.util.List;

import org.junit.Test;

/**
 * This class tests the abstract ThriftSender.
 */
public class ThriftSenderTest {

  @Test(expected = SenderException.class)
  public void calculateProcessSizeNull() throws Exception {
    ThriftSender sender = new ThriftSender(ProtocolType.Compact, 0) {
      @Override
      public void send(Process process, List<Span> spans) throws SenderException {
      }
    };

    sender.calculateProcessSize(null);
  }

  @Test(expected = SenderException.class)
  public void calculateSpanSizeNull() throws Exception {
    ThriftSender sender = new ThriftSender(ProtocolType.Compact, 0) {
      @Override
      public void send(Process process, List<Span> spans) throws SenderException {
      }
    };

    sender.calculateSpanSize(null);
  }

}
