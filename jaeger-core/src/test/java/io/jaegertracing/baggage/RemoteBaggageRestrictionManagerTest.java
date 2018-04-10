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

package io.jaegertracing.baggage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import io.jaegertracing.baggage.http.BaggageRestrictionResponse;
import io.jaegertracing.exceptions.BaggageRestrictionManagerException;
import io.jaegertracing.metrics.InMemoryStatsReporter;
import io.jaegertracing.metrics.Metrics;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RemoteBaggageRestrictionManagerTest {
  @Mock private BaggageRestrictionManagerProxy baggageRestrictionProxy;
  private Metrics metrics;
  private InMemoryStatsReporter metricsReporter;
  private static final String SERVICE_NAME = "service";
  private static final String BAGGAGE_KEY = "key";
  private static final int MAX_VALUE_LENGTH = 10;
  private static final BaggageRestrictionResponse RESTRICTION =
      new BaggageRestrictionResponse(BAGGAGE_KEY, MAX_VALUE_LENGTH);

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
  public void testUpdateBaggageRestrictions() throws Exception {
    when(baggageRestrictionProxy.getBaggageRestrictions(SERVICE_NAME))
        .thenReturn(new ArrayList<BaggageRestrictionResponse>(Arrays.asList(RESTRICTION)));
    undertest.updateBaggageRestrictions();

    assertEquals(Restriction.of(true, MAX_VALUE_LENGTH), undertest.getRestriction(SERVICE_NAME, BAGGAGE_KEY));
    assertFalse(undertest.getRestriction(SERVICE_NAME, "bad-key").isKeyAllowed());
    assertTrue(
        metricsReporter.counters.get("jaeger:baggage_restrictions_updates.result=ok") > 0L);
  }

  @Test
  public void testAllowBaggageOnInitializationFailure() throws Exception {
    when(baggageRestrictionProxy.getBaggageRestrictions(SERVICE_NAME))
        .thenThrow(new BaggageRestrictionManagerException("error"));
    undertest = new RemoteBaggageRestrictionManager(SERVICE_NAME, baggageRestrictionProxy, metrics,
        false, 60000, 60000);

    assertTrue(undertest.getRestriction(SERVICE_NAME, BAGGAGE_KEY).isKeyAllowed());
    undertest.updateBaggageRestrictions();
    assertFalse(undertest.isReady());
    // If baggage restriction update fails, all baggage should still be allowed.
    assertTrue(undertest.getRestriction(SERVICE_NAME, BAGGAGE_KEY).isKeyAllowed());
    assertTrue(metricsReporter.counters.get("jaeger:baggage_restrictions_updates.result=err") > 0L);
  }

  @Test
  public void testDenyBaggageOnInitializationFailure() throws Exception {
    when(baggageRestrictionProxy.getBaggageRestrictions(SERVICE_NAME))
        .thenReturn(new ArrayList<BaggageRestrictionResponse>(Arrays.asList(RESTRICTION)));
    undertest = new RemoteBaggageRestrictionManager(SERVICE_NAME, baggageRestrictionProxy, metrics,
        true, 60000, 60000);

    assertFalse(undertest.getRestriction(SERVICE_NAME, BAGGAGE_KEY).isKeyAllowed());
    undertest.updateBaggageRestrictions();
    assertTrue(undertest.isReady());
    assertTrue(undertest.getRestriction(SERVICE_NAME, BAGGAGE_KEY).isKeyAllowed());
  }
}
