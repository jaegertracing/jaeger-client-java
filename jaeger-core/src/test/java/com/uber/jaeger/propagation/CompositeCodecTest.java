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

package com.uber.jaeger.propagation;

import static org.junit.Assert.assertEquals;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uber.jaeger.SpanContext;

import io.opentracing.propagation.TextMap;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CompositeCodecTest {

  @Mock
  private SpanContext mockContext;

  @Mock
  private TextMap mockCarrier;

  @Mock
  private Codec<TextMap> mockCodec1;

  @Mock
  private Codec<TextMap> mockCodec2;

  @Test
  public void testInject() {
    CompositeCodec<TextMap> composite = new CompositeCodec<TextMap>(Arrays.asList(mockCodec1, mockCodec2));
    composite.inject(mockContext, mockCarrier);
    verify(mockCodec1, times(1)).inject(mockContext, mockCarrier);
    verify(mockCodec2, times(1)).inject(mockContext, mockCarrier);
  }

  @Test
  public void testExtractFromFirstCodec() {
    when(mockCodec1.extract(mockCarrier)).thenReturn(mockContext);
    CompositeCodec<TextMap> composite = new CompositeCodec<TextMap>(Arrays.asList(mockCodec1, mockCodec2));
    assertEquals(mockContext, composite.extract(mockCarrier));
    verify(mockCodec1, times(1)).extract(mockCarrier);
    verify(mockCodec2, times(0)).extract(mockCarrier);
  }

  @Test
  public void testExtractFromSecondCodec() {
    when(mockCodec2.extract(mockCarrier)).thenReturn(mockContext);
    CompositeCodec<TextMap> composite = new CompositeCodec<TextMap>(Arrays.asList(mockCodec1, mockCodec2));
    assertEquals(mockContext, composite.extract(mockCarrier));
    verify(mockCodec1, times(1)).extract(mockCarrier);
    verify(mockCodec2, times(1)).extract(mockCarrier);
  }

}
