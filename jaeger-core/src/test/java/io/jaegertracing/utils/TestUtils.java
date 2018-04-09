/*
 * Copyright (c) 2017, The Jaeger Authors
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

package io.jaegertracing.utils;

import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Field;

/**
 * @deprecated Use io.opentracing.util.GlobalTracerTestUtil
 */
@Deprecated
public class TestUtils {
  public static void resetGlobalTracer() throws NoSuchFieldException, IllegalAccessException {
    // Reset opentracing's global tracer
    Field field = GlobalTracer.class.getDeclaredField("tracer");
    field.setAccessible(true);
    field.set(null, NoopTracerFactory.create());
  }
}
