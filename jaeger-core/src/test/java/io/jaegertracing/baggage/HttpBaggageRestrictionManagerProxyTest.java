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
import static org.junit.Assert.assertNotNull;

import io.jaegertracing.baggage.http.BaggageRestrictionResponse;
import io.jaegertracing.exceptions.BaggageRestrictionManagerException;
import io.jaegertracing.mocks.MockAgentResource;
import java.net.URI;
import java.util.List;
import java.util.Properties;
import javax.ws.rs.core.Application;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;


public class HttpBaggageRestrictionManagerProxyTest extends JerseyTest {

  private HttpBaggageRestrictionManagerProxy undertest;
  private BaggageRestrictionResponse expectedRestriction = new BaggageRestrictionResponse("key", 10);

  private static Properties originalProps;

  @BeforeClass
  public static void beforeClass() {
    Properties originalProps = new Properties(System.getProperties());
    if (System.getProperty(TestProperties.CONTAINER_PORT) == null) {
      System.setProperty(TestProperties.CONTAINER_PORT, "0");
    }
  }

  @AfterClass
  public static void afterClass() {
    System.setProperties(originalProps);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    undertest = new HttpBaggageRestrictionManagerProxy(null);
  }

  @Override
  protected Application configure() {
    return new ResourceConfig(MockAgentResource.class);
  }

  @Test
  public void testGetBaggageRestrictions() throws Exception {
    URI uri = target().getUri();
    undertest = new HttpBaggageRestrictionManagerProxy(uri.getHost() + ":" + uri.getPort());
    List<BaggageRestrictionResponse> response = undertest.getBaggageRestrictions("clairvoyant");
    assertNotNull(response);
    assertEquals(1, response.size());
    assertEquals(expectedRestriction, response.get(0));
  }

  @Test(expected = BaggageRestrictionManagerException.class)
  public void testGetSamplingStrategyError() throws Exception {
    URI uri = target().getUri();
    undertest = new HttpBaggageRestrictionManagerProxy(uri.getHost() + ":" + uri.getPort());
    undertest.getBaggageRestrictions("");
  }

  @Test(expected = BaggageRestrictionManagerException.class)
  public void testParseInvalidJson() throws Exception {
    undertest.parseJson("invalid json");
  }
}
