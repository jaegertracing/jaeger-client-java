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
import static org.junit.Assert.assertNotNull;

import com.uber.jaeger.baggage.http.BaggageRestriction;
import com.uber.jaeger.exceptions.BaggageRestrictionErrorException;
import com.uber.jaeger.mocks.MockAgentResource;

import java.net.URI;
import java.util.List;
import java.util.Properties;

import javax.ws.rs.core.Application;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;


public class HttpBaggageRestrictionProxyTest extends JerseyTest {

  private HttpBaggageRestrictionProxy undertest = new HttpBaggageRestrictionProxy(null);
  private BaggageRestriction expectedRestriction = new BaggageRestriction("key", 10);

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
  protected Application configure() {
    return new ResourceConfig(MockAgentResource.class);
  }

  @Test
  public void testGetBaggageRestrictions() throws Exception {
    URI uri = target().getUri();
    undertest = new HttpBaggageRestrictionProxy(uri.getHost() + ":" + uri.getPort());
    List<BaggageRestriction> response = undertest.getBaggageRestrictions("clairvoyant");
    assertNotNull(response);
    assertEquals(1, response.size());
    assertEquals(expectedRestriction, response.get(0));
  }

  @Test (expected = BaggageRestrictionErrorException.class)
  public void testGetSamplingStrategyError() throws Exception {
    URI uri = target().getUri();
    undertest = new HttpBaggageRestrictionProxy(uri.getHost() + ":" + uri.getPort());
    undertest.getBaggageRestrictions("");
  }

  @Test (expected = BaggageRestrictionErrorException.class)
  public void testParseInvalidJson() throws Exception {
    undertest.parseJson("invalid json");
  }
}
