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

package io.jaegertracing.internal.utils;

import io.jaegertracing.internal.exceptions.EmptyIpException;
import io.jaegertracing.internal.exceptions.NotFourOctetsException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

public class Utils {
  public static String normalizeBaggageKey(String key) {
    return key.replaceAll("_", "-").toLowerCase(Locale.ROOT);
  }

  public static int ipToInt(String ip) throws EmptyIpException, NotFourOctetsException {
    if (ip.equals("")) {
      throw new EmptyIpException();
    }

    if (ip.equals("localhost")) {
      return (127 << 24) | 1;
    }

    InetAddress octets;
    try {
      octets = InetAddress.getByName(ip);
    } catch (UnknownHostException e) {
      throw new NotFourOctetsException();
    }

    int intIp = 0;
    for (byte octet : octets.getAddress()) {
      intIp = (intIp << 8) | (octet & 0xFF);
    }
    return intIp;
  }

  public static long uniqueId() {
    long val = 0;
    while (val == 0) {
      val = Java6CompatibleThreadLocalRandom.current().nextLong();
    }
    return val;
  }

  public static boolean equals(Object a, Object b) {
    return (a == b) || (a != null && a.equals(b));
  }

  private Utils() {}
}
