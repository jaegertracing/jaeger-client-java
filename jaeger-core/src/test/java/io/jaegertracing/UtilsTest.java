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

package io.jaegertracing;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import io.jaegertracing.exceptions.EmptyIpException;
import io.jaegertracing.exceptions.NotFourOctetsException;
import io.jaegertracing.utils.Utils;
import org.junit.Test;

public class UtilsTest {

  @Test
  public void testNormalizeBaggageKey() {
    String expectedKey = "test-key";
    String actualKey = Utils.normalizeBaggageKey("TEST_KEY");
    assertEquals(expectedKey, actualKey);
  }

  @Test(expected = NotFourOctetsException.class)
  public void testIpToInt32NotFourOctets() {
    Utils.ipToInt(":19");
  }

  @Test(expected = EmptyIpException.class)
  public void testIpToInt32EmptyIpException() {
    Utils.ipToInt("");
  }

  @Test
  public void testIpToInt32_localhost() {
    assertThat(Utils.ipToInt("127.0.0.1"), equalTo((127 << 24) | 1));
  }

  @Test
  public void testIpToInt32_above127() {
    assertThat(Utils.ipToInt("10.137.1.2"), equalTo((10 << 24) | (137 << 16) | (1 << 8) | 2));
  }

  @Test
  public void testIpToInt32_zeros() {
    assertThat(Utils.ipToInt("0.0.0.0"), equalTo(0));
  }

  @Test
  public void testIpToInt32_broadcast() {
    assertThat(Utils.ipToInt("255.255.255.255"), equalTo(-1));
  }
}
