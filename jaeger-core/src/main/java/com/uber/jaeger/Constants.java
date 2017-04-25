/*
 * Copyright (c) 2016, Uber Technologies, Inc
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
package com.uber.jaeger;

public class Constants {
  // TODO these should be configurable
  public static final String X_UBER_SOURCE = "x-uber-source";

  public static final int MAX_TAG_LENGTH = 256;

  /** Span tag key to describe the type of sampler used on the root span. */
  public static final String SAMPLER_TYPE_TAG_KEY = "sampler.type";

  /** Span tag key to describe the parameter of the sampler used on the root span. */
  public static final String SAMPLER_PARAM_TAG_KEY = "sampler.param";

  /**
   * The name of HTTP header or a TextMap carrier key which, if found in the carrier, forces the
   * trace to be sampled as "debug" trace. The value of the header is recorded as the tag on the
   * root span, so that the trace can be found in the UI using this value as a correlation ID.
   */
  public static final String DEBUG_ID_HEADER_KEY = "jaeger-debug-id";
}
