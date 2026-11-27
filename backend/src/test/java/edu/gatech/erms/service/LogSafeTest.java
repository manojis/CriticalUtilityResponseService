package edu.gatech.erms.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogSafeTest {

  @Test
  void lineBreaksAreNeutralized() {
    assertThat(LogSafe.of("eve\r\nINFO forged entry")).isEqualTo("eve__INFO forged entry");
    assertThat(LogSafe.of("alice")).isEqualTo("alice");
    assertThat(LogSafe.of(null)).isNull();
  }
}
