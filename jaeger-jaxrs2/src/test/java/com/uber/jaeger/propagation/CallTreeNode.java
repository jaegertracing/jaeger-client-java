/*
 * Copyright (c) 2016, Uber Technologies, Inc
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
