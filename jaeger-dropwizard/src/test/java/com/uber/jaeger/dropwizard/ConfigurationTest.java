package com.uber.jaeger.dropwizard;

import org.junit.Test;

public class ConfigurationTest {

  @Test
  public void testInstantiableWithNulls() throws Exception {
    new Configuration("serviceName", null, null, null);
  }
}
