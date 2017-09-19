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

package com.uber.jaeger.utils;

import com.uber.jaeger.exceptions.EmptyIpException;
import com.uber.jaeger.exceptions.NotFourOctetsException;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Utils {
  public static String normalizeBaggageKey(String key) {
    return key.replaceAll("_", "-").toLowerCase();
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
