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
package com.uber.jaeger.crossdock.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.jaeger.crossdock.api.Downstream;
import com.uber.jaeger.crossdock.api.StartTraceRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class StartTraceRequestDeserializer extends JsonDeserializer<StartTraceRequest> {
  private static final Logger logger = LoggerFactory.getLogger(StartTraceRequestDeserializer.class);

  private static final ObjectMapper mapper = new ObjectMapper();

  @Override
  public StartTraceRequest deserialize(JsonParser jp, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);

    logger.info("start trace json request: {}", node);

    String serverRole = node.get("serverRole").asText();
    boolean sampled = node.get("sampled").asBoolean();
    String baggage = node.get("baggage").asText();

    JsonNode downstreamNode = node.get("downstream");
    Downstream downstream = null;
    if (downstreamNode != null) {
      downstream = mapper.readerFor(Downstream.class).readValue(downstreamNode);
    }

    return new StartTraceRequest(serverRole, sampled, baggage, downstream);
  }
}
