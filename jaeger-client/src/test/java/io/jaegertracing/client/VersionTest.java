/*
 * Copyright (c) 2018, The Jaeger Authors
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

package io.jaegertracing.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import io.jaegertracing.Configuration;
import io.jaegertracing.internal.JaegerTracer;
import org.junit.Test;

public class VersionTest {
  @Test
  public void testVersionGet() {
    assertEquals(
        "Version should be the same as the properties file",
        JaegerTracer.getVersionFromProperties(),
        Version.get()
    );

    assertNotEquals(
        "The version from the tracer should not be the same string as Version.get()",
        ((JaegerTracer) new Configuration("testVersionGet").getTracer()).getVersion(),
        Version.get()
    );
  }

}
