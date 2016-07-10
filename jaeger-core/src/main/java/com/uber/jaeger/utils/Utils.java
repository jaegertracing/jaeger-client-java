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

import com.uber.jaeger.exceptions.EmptyIPException;
import com.uber.jaeger.exceptions.NotFourOctetsException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class Utils {
    public static String normalizeBaggageKey(String key) {
        return key.replaceAll("_", "-").toLowerCase();
    }

    public static int ipToInt(String ip) throws EmptyIPException, NotFourOctetsException {
        if (ip.equals("")) {
            throw new EmptyIPException();
        }

        if (ip.equals("localhost")) {
            return (127 << 24) | 1;
        }

        InetAddress octets;
        try {
            octets = InetAddress.getByName(ip);
        } catch(UnknownHostException e)  {
            throw new NotFourOctetsException();
        }

        int intIP = 0;
        for (byte octet: octets.getAddress()) {
            intIP = (intIP << 8) | (octet);
        }
        return intIP;
    }

    public static long uniqueID() {
        long val = 0;
        while(val == 0) {
            val = ThreadLocalRandom.current().nextLong();
        }
        return val;
    }

    public static long getMicroseconds() {
        return TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()) + (TimeUnit.NANOSECONDS.toMicros(System.nanoTime()) % 1000);
    }

    public static long getNanoseconds() {
        return TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()) + (System.nanoTime() % 1000000);
    }
}