package edu.gatech.erms.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the {@link Clock} every "today" value is derived from, so dates are computed per call in
 * {@code erms.time-zone} (legacy defect R3) and tests can control time.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

  @Bean
  Clock clock(ErmsProperties properties) {
    return Clock.system(ZoneId.of(properties.timeZone()));
  }
}
