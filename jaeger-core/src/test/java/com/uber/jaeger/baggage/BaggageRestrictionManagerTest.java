/*
 * Copyright (c) 2017, Uber Technologies, Inc
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

package com.uber.jaeger.baggage;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BaggageRestrictionManagerTest {
  private static final String BAGGAGE_KEY = "key";
  private static final String BAGGAGE_VALUE = "value";

  private DefaultBaggageRestrictionManager undertest = new DefaultBaggageRestrictionManager();

  @Test
  public void testSanitizeBaggage() {
    SanitizedBaggage actual = undertest.sanitizeBaggage(BAGGAGE_KEY, BAGGAGE_VALUE);
    SanitizedBaggage expected = SanitizedBaggage.of(true, BAGGAGE_VALUE, false);
    assertEquals(expected, actual);

    final String value = "01234567890123456789";
    final String truncated = "0123456789";
    final int max_value_length = 10;

    actual = undertest.truncateBaggage(value, max_value_length);
    expected = SanitizedBaggage.of(true, truncated, true);
    assertEquals(expected, actual);
  }
}
