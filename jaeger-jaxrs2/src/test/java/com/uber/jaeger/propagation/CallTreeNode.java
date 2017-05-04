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

package com.uber.jaeger.propagation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.ext.Provider;

/*
 * This class is used for presenting individual spans, and capturing the recursive
 * chain of calls in its "downstream" list.  Additionaly BAGGAGE is also captured in
 * a request, and propagated up the chain of requests.
 */
@Provider
public class CallTreeNode {
  @JsonProperty("actor")
  String actor;

  @JsonProperty("span")
  SpanInfo span;

  @JsonProperty("~leaf")
  boolean leaf;

  @JsonProperty("~downstream")
  List<CallTreeNode> downstream = new ArrayList<>();

  // Need to provide a dummy constructor to make readEntity, and jackson work properly.
  public CallTreeNode() {
    leaf = true;
  }

  @JsonCreator
  public CallTreeNode(
      @JsonProperty("actor") String actor, @JsonProperty("span") SpanInfo spanInfo) {
    this.actor = actor;
    span = spanInfo;
    leaf = true;
  }

  public CallTreeNode(String actor, SpanInfo spanInfo, List<CallTreeNode> downstream) {
    this(actor, spanInfo);
    if (downstream == null) {
      leaf = true;
    }
  }

  public List<CallTreeNode> getDownstream() {
    return downstream;
  }

  public String getActor() {
    return actor;
  }

  public Boolean isLeaf() {
    return leaf;
  }

  public boolean validateTraceIds(String traceId, boolean sampled) {
    boolean valid =
        span.getTraceId().equals(traceId)
            && span.getBaggage().equals(FilterIntegrationTest.BAGGAGE_VALUE)
            && span.getSampled() == sampled;

    if (!isLeaf()) {
      List<CallTreeNode> downstream = this.getDownstream();

      if (downstream.size() == 0) {
        throw new IllegalStateException(
            "Node %s id is not a leaf, but downstream is Empty".format(this.toString()));
      }

      for (CallTreeNode downStreamTreeNode : downstream) {
        valid = valid && downStreamTreeNode.validateTraceIds(traceId, sampled);

        if (!valid) {
          return false;
        }
      }
    }

    return valid;
  }
}
