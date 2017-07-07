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

package com.uber.jaeger.mocks;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;

@Path("")
public class MockAgentResource {
  @GET
  @Path("")
  @Produces(MediaType.APPLICATION_JSON)
  public String getServiceSamplingStrategy(@QueryParam("service") String serviceName) {

    if (serviceName.equals("clairvoyant")) {
      return "{\"strategyType\":0,\"probabilisticSampling\":{\"samplingRate\":0.001},\"rateLimitingSampling\":null}";
    }

    throw new WebApplicationException();
  }

  @GET
  @Path("baggageRestrictions")
  @Produces(MediaType.APPLICATION_JSON)
  public String getBaggageRestrictions(@QueryParam("service") String serviceName) {

    if (serviceName.equals("clairvoyant")) {
      return "[{\"baggageKey\":\"key\",\"maxValueLength\":\"10\"}]";
    }

    throw new WebApplicationException();
  }
}
