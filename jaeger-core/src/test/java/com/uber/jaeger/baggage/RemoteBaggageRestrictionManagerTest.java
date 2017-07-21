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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.uber.jaeger.baggage.http.BaggageRestriction;
import com.uber.jaeger.exceptions.BaggageRestrictionException;
import com.uber.jaeger.metrics.InMemoryStatsReporter;
import com.uber.jaeger.metrics.Metrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RemoteBaggageRestrictionManagerTest {
  @Mock private BaggageRestrictionProxy baggageRestrictionProxy;
  private Metrics metrics;
  private InMemoryStatsReporter metricsReporter;
  private static final String SERVICE_NAME = "service";
  private static final String BAGGAGE_KEY = "key";
  private static final String BAGGAGE_VALUE = "value";
  private static final int MAX_VALUE_LENGTH = 10;
  private static final BaggageRestriction RESTRICTION = new BaggageRestriction(BAGGAGE_KEY, MAX_VALUE_LENGTH);

  private RemoteBaggageRestrictionManager undertest;

  @Before
  public void setUp() throws Exception {
    metricsReporter = new InMemoryStatsReporter();
    metrics = Metrics.fromStatsReporter(metricsReporter);
    undertest = new RemoteBaggageRestrictionManager(SERVICE_NAME, baggageRestrictionProxy, metrics,
        false);
  }

  @After
  public void tearDown() {
    undertest.close();
  }

  @Test
  public void testSanitizeBaggage() throws Exception {
    when(baggageRestrictionProxy.getBaggageRestrictions(SERVICE_NAME))
        .thenReturn(new ArrayList<BaggageRestriction>(Arrays.asList(RESTRICTION)));
    undertest.updateBaggageRestrictions();

    final String value = "01234567890123456789";
    final String truncated = "0123456789";

    Map<String, String> expectedFields = new HashMap<String, String>();
    expectedFields.put("event", "baggage");
    expectedFields.put("key", BAGGAGE_KEY);
    expectedFields.put("value", truncated);
    expectedFields.put("override", "true");
    expectedFields.put("truncated", "true");

    SanitizedBaggage expected = SanitizedBaggage.of(true, BAGGAGE_KEY, truncated, expectedFields, true);
    assertEquals(expected, undertest.sanitizeBaggage(BAGGAGE_KEY, value, "prev"));

    String invalidKey = "llave";
    expected = SanitizedBaggage.of(false, null, null, null, false);
    assertEquals(expected, undertest.sanitizeBaggage(invalidKey, BAGGAGE_VALUE, null));
    assertTrue(
        metricsReporter.counters.get("jaeger.baggage-restrictions-update.result=ok") > 0L);
  }

  @Test
  public void testAllowBaggageOnInitializationFailure() throws Exception {
    when(baggageRestrictionProxy.getBaggageRestrictions(SERVICE_NAME))
        .thenThrow(new BaggageRestrictionException("error"));
    undertest = new RemoteBaggageRestrictionManager(SERVICE_NAME, baggageRestrictionProxy, metrics,false);

    Map<String, String> expectedFields = new HashMap<String, String>();
    expectedFields.put("event", "baggage");
    expectedFields.put("key", BAGGAGE_KEY);
    expectedFields.put("value", BAGGAGE_VALUE);

    assertEquals(SanitizedBaggage.of(true, BAGGAGE_KEY, BAGGAGE_VALUE, expectedFields, false),
        undertest.sanitizeBaggage(BAGGAGE_KEY, BAGGAGE_VALUE, null));
    assertTrue(metricsReporter.counters.get("jaeger.baggage-restrictions-update.result=err") > 0L);
  }

  @Test
  public void testDenyBaggageOnInitializationFailure() throws Exception {
    when(baggageRestrictionProxy.getBaggageRestrictions(SERVICE_NAME))
        .thenThrow(new BaggageRestrictionException("error"));
    undertest = new RemoteBaggageRestrictionManager(SERVICE_NAME, baggageRestrictionProxy, metrics,
        true, 60000);

    assertEquals(SanitizedBaggage.of(false, null, null, null, false),
        undertest.sanitizeBaggage(BAGGAGE_KEY, BAGGAGE_VALUE, null));
    assertTrue(
        metricsReporter.counters.get("jaeger.baggage-restrictions-update.result=err") > 0L);
  }
}
