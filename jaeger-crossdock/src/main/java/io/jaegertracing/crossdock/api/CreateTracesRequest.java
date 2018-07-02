/*
 * Copyright (c) 2017, Uber Technologies, Inc
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

package io.jaegertracing.crossdock.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.jaegertracing.internal.samplers.RemoteControlledSampler;
import java.util.Map;
import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
public class CreateTracesRequest {
  private String type = RemoteControlledSampler.TYPE;
  private String operation;
  private int count;
  private Map<String, String> tags;

  @JsonCreator
  public CreateTracesRequest(
      @JsonProperty("type") String type,
      @JsonProperty("operation") String operation,
      @JsonProperty("count") int count,
      @JsonProperty("tags") Map<String, String> tags) {
    this.type = type;
    this.operation = operation;
    this.count = count;
    this.tags = tags;
  }
}
