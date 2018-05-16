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

package io.jaegertracing;

import static org.junit.Assert.assertEquals;

import io.jaegertracing.reporters.InMemoryReporter;
import io.jaegertracing.samplers.ConstSampler;
import io.jaegertracing.utils.Utils;
import java.net.Inet4Address;
import java.net.InetAddress;
import org.junit.Test;

public class TracerTagsTest {

  @Test
  public void testTracerTags() throws Exception {
    InMemoryReporter spanReporter = new InMemoryReporter();
    Tracer tracer = new Tracer.Builder("x", spanReporter, new ConstSampler(true))
        .withZipkinSharedRpcSpan()
        .withTag("tracer.tag.str", "y")
        .build();

    Span span = (Span) tracer.buildSpan("root").start();

    // span should only contain sampler tags and no tracer tags
    assertEquals(2, span.getTags().size());
    assertEquals(true, span.getTags().containsKey("sampler.type"));
    assertEquals(true, span.getTags().containsKey("sampler.param"));
    assertEquals(false, span.getTags().containsKey("tracer.tag.str"));
  }

  @Test
  public void testDefaultHostTags() throws Exception {
    InMemoryReporter spanReporter = new InMemoryReporter();
    Tracer tracer = new Tracer.Builder("x")
        .withReporter(spanReporter)
        .build();
    assertEquals(tracer.getHostName(), tracer.tags().get(Constants.TRACER_HOSTNAME_TAG_KEY));
    assertEquals(InetAddress.getLocalHost().getHostAddress(), tracer.tags().get(Constants.TRACER_IP_TAG_KEY));
    assertEquals(Utils.ipToInt(Inet4Address.getLocalHost().getHostAddress()), tracer.getIpv4());
  }

  @Test
  public void testDeclaredHostTags() throws Exception {
    InMemoryReporter spanReporter = new InMemoryReporter();
    String hostname = "myhost";
    String ip = "1.1.1.1";
    Tracer tracer = new Tracer.Builder("x")
        .withReporter(spanReporter)
        .withTag(Constants.TRACER_HOSTNAME_TAG_KEY, hostname)
        .withTag(Constants.TRACER_IP_TAG_KEY, ip)
        .build();
    assertEquals(hostname, tracer.tags().get(Constants.TRACER_HOSTNAME_TAG_KEY));
    assertEquals(ip, tracer.tags().get(Constants.TRACER_IP_TAG_KEY));
    assertEquals(Utils.ipToInt(ip), tracer.getIpv4());
  }

  @Test
  public void testEmptyDeclaredIpTag() throws Exception {
    InMemoryReporter spanReporter = new InMemoryReporter();
    String ip = "";
    Tracer tracer = new Tracer.Builder("x")
            .withReporter(spanReporter)
            .withTag(Constants.TRACER_IP_TAG_KEY, ip)
            .build();
    assertEquals(0, tracer.getIpv4());
  }

  @Test
  public void testShortDeclaredIpTag() throws Exception {
    InMemoryReporter spanReporter = new InMemoryReporter();
    String ip = ":19";
    Tracer tracer = new Tracer.Builder("x")
            .withReporter(spanReporter)
            .withTag(Constants.TRACER_IP_TAG_KEY, ip)
            .build();
    assertEquals(0, tracer.getIpv4());
  }
}
