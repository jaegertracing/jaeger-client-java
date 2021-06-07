package io.jaegertracing.thrift.internal.senders;

import io.jaegertracing.Configuration;
import io.jaegertracing.spi.Sender;
import io.jaegertracing.spi.SenderFactory;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.transport.TTransportException;

/**
 * Factory for {@link Sender} instances backed by Thrift. The actual sender implementation can be either
 * {@link HttpSender} or {@link UdpSender}, based on the given {@link Configuration.SenderConfiguration}.
 */
@Slf4j
@ToString
public class ThriftSenderFactory implements SenderFactory {
  @Override
  public Sender getSender(Configuration.SenderConfiguration conf) {
    if (null != conf.getEndpoint() && !conf.getEndpoint().isEmpty()) {
      HttpSender.Builder httpSenderBuilder = new HttpSender.Builder(conf.getEndpoint());
      if (null != conf.getAuthUsername() && !conf.getAuthUsername().isEmpty()
          && null != conf.getAuthPassword() && !conf.getAuthPassword().isEmpty()) {
        log.debug("Using HTTP Basic authentication with data from the environment variables.");
        httpSenderBuilder.withAuth(conf.getAuthUsername(), conf.getAuthPassword());
      } else if (null != conf.getAuthToken() && !conf.getAuthToken().isEmpty()) {
        log.debug("Auth Token environment variable found.");
        httpSenderBuilder.withAuth(conf.getAuthToken());
      }

      log.debug("Using the HTTP Sender to send spans directly to the endpoint.");
      try {
        return httpSenderBuilder.build();
      }
      catch (TTransportException e) {
        throw new RuntimeException("Failed to build http sender: " + e, e);
      }
    }

    log.debug("Using the UDP Sender to send spans to the agent.");
    try {
      return new UdpSender(
              stringOrDefault(conf.getAgentHost(), UdpSender.DEFAULT_AGENT_UDP_HOST),
              numberOrDefault(conf.getAgentPort(), UdpSender.DEFAULT_AGENT_UDP_COMPACT_PORT).intValue(),
              0 /* max packet size */);
    }
    catch (TTransportException e) {
      throw new RuntimeException("Unable to create UDPSender: " + e, e);
    }
  }

  @Override
  public String getType() {
    return "thrift";
  }

  private static String stringOrDefault(String value, String defaultValue) {
    return value != null && value.length() > 0 ? value : defaultValue;
  }

  private static Number numberOrDefault(Number value, Number defaultValue) {
    return value != null ? value : defaultValue;
  }
}
