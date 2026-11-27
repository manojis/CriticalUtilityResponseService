package edu.gatech.erms.security;

import static org.assertj.core.api.Assertions.assertThat;

import edu.gatech.erms.support.TestData;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** The demo seed's BCrypt hashes must verify for "password" (docs/DATA_MODEL.md, Demo seed). */
class DemoSeedPasswordTest {

  private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

  @Test
  void everyDemoSeedHashMatchesPassword() throws IOException {
    String seed;
    try (InputStream in = getClass().getResourceAsStream("/db/seed/R__demo_seed.sql")) {
      assertThat(in).as("demo seed on classpath").isNotNull();
      seed = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    Matcher matcher = BCRYPT.matcher(seed);
    List<String> hashes =
        Stream.iterate(matcher, Matcher::find, m -> m).map(Matcher::group).toList();

    assertThat(hashes).hasSize(4);
    assertThat(hashes).allSatisfy(hash -> assertThat(encoder.matches("password", hash)).isTrue());
  }

  @Test
  void testFixtureHashMatchesPassword() {
    assertThat(encoder.matches("password", TestData.PASSWORD_HASH)).isTrue();
    assertThat(encoder.matches("Password", TestData.PASSWORD_HASH)).isFalse();
  }
}
