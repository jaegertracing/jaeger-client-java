/*
 * Copyright (c) 2020, The Jaeger Authors
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

package io.jaegertracing.internal.clock;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SystemClockTest {
  @Parameters(name = "java.version {0}: major is {1}")
  public static Collection<Object[]> version() {
    return Arrays.asList(new Object[][] {     
        { "9", 9 }, 
        { "16-ea", 16 }, 
        { "11.0.5", 11 }, 
        { "1.8.0_231", 8 }
    });
  }
    
  private String property;
  private int version;

  public SystemClockTest(String property, int version) {
    this.property = property;
    this.version = version;
  }
  
  @Test
  public void test() {
    assertThat(SystemClock.parseJavaVersion(property), equalTo(version));
  }
}
