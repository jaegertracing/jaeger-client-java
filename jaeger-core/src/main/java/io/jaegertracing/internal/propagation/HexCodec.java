/*
 * Copyright (c) 2017, Uber Technologies, Inc
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

package io.jaegertracing.internal.propagation;

import lombok.extern.slf4j.Slf4j;

// copy/pasted from brave.internal.HexCodec 4.1.1 to avoid build complexity
@Slf4j
final class HexCodec {

  /**
   * Parses a 1 to 32 character lower-hex string with no prefix into an unsigned long, tossing any
   * bits higher than 64.
   *
   * @return a 64 bit long, meaning that negative values are the overflow of Java's 32 bit long
   */
  static Long lowerHexToUnsignedLong(String lowerHex) {
    int length = lowerHex.length();
    if (length < 1 || length > 32) {
      log.debug("token {} size is out of bounds [1, 32]", lowerHex);
      return null;
    }

    // trim off any high bits
    int beginIndex = length > 16 ? length - 16 : 0;

    return hexToUnsignedLong(lowerHex, beginIndex, Math.min(beginIndex + 16, lowerHex.length()));
  }

  /**
   * Parses a 1 to 32 character higher-hex string with no prefix into an unsigned long, tossing any
   * bits lower than 64.
   *
   * @return a 64 bit long, meaning that negative values are the overflow of Java's 32 bit long
   */
  static Long higherHexToUnsignedLong(String higherHex) {
    int length = higherHex.length();
    if (length > 32 || length < 1) {
      log.debug("token {} size is out of bounds [1, 32]", higherHex);
      return null;
    }

    return hexToUnsignedLong(higherHex, 0, Math.max(length - 16, 0));
  }

  /**
   * Parses a 16 character lower-hex string with no prefix into an unsigned long, starting at the
   * spe index.
   *
   * @return a 64 bit long, meaning that negative values are the overflow of Java's 32 bit long
   */
  static Long hexToUnsignedLong(String lowerHex, int index, int endIndex) {
    long result = 0;
    for (; index < endIndex; index++) {
      char c = lowerHex.charAt(index);
      result <<= 4;
      if (c >= '0' && c <= '9') {
        result |= c - '0';
      } else if (c >= 'a' && c <= 'f') {
        result |= c - 'a' + 10;
      } else {
        return null;
      }
    }
    return result;
  }

  /**
   * Returns 16 or 32 character hex string depending on if {@code high} is zero.
   */
  static String toLowerHex(long high, long low) {
    char[] result = new char[high != 0 ? 32 : 16];
    int pos = 0;
    if (high != 0) {
      writeHexLong(result, pos, high);
      pos += 16;
    }
    writeHexLong(result, pos, low);
    return new String(result);
  }

  /**
   * Inspired by {@code okio.Buffer.writeLong}
   */
  static String toLowerHex(long v) {
    char[] data = new char[16];
    writeHexLong(data, 0, v);
    return new String(data);
  }

  /**
   * Inspired by {@code okio.Buffer.writeLong}
   */
  static void writeHexLong(char[] data, int pos, long v) {
    writeHexByte(data, pos + 0, (byte) ((v >>> 56L) & 0xff));
    writeHexByte(data, pos + 2, (byte) ((v >>> 48L) & 0xff));
    writeHexByte(data, pos + 4, (byte) ((v >>> 40L) & 0xff));
    writeHexByte(data, pos + 6, (byte) ((v >>> 32L) & 0xff));
    writeHexByte(data, pos + 8, (byte) ((v >>> 24L) & 0xff));
    writeHexByte(data, pos + 10, (byte) ((v >>> 16L) & 0xff));
    writeHexByte(data, pos + 12, (byte) ((v >>> 8L) & 0xff));
    writeHexByte(data, pos + 14, (byte) (v & 0xff));
  }

  static final char[] HEX_DIGITS =
      {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

  static void writeHexByte(char[] data, int pos, byte b) {
    data[pos + 0] = HEX_DIGITS[(b >> 4) & 0xf];
    data[pos + 1] = HEX_DIGITS[b & 0xf];
  }

  HexCodec() {
  }
}
