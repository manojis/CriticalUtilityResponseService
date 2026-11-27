package edu.gatech.erms.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JdbcSupportTest {

  @Test
  void likeWildcardsAreEscaped_S9() {
    assertThat(JdbcSupport.containsPattern("100%")).isEqualTo("%100\\%%");
    assertThat(JdbcSupport.containsPattern("a_b")).isEqualTo("%a\\_b%");
    assertThat(JdbcSupport.containsPattern("c:\\x")).isEqualTo("%c:\\\\x%");
    assertThat(JdbcSupport.containsPattern("crane")).isEqualTo("%crane%");
  }

  @Test
  void distanceIsRoundedToOneDecimal() {
    assertThat(ResourceRepository.roundToTenth(3.249)).isEqualTo(3.2);
    assertThat(ResourceRepository.roundToTenth(3.25)).isEqualTo(3.3);
    assertThat(ResourceRepository.roundToTenth(0.0)).isEqualTo(0.0);
  }
}
