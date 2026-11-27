package edu.gatech.erms.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class DomainTest {

  @Test
  void roleParsesCaseInsensitively() {
    assertThat(RequestRole.fromParameter("requester")).isEqualTo(RequestRole.REQUESTER);
    assertThat(RequestRole.fromParameter(" OWNER ")).isEqualTo(RequestRole.OWNER);
    assertThatThrownBy(() -> RequestRole.fromParameter("admin"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void pageResultComputesTotalPages() {
    assertThat(new PageResult<>(List.of(), 0, 20, 0).totalPages()).isZero();
    assertThat(new PageResult<>(List.of(), 0, 20, 20).totalPages()).isEqualTo(1);
    assertThat(new PageResult<>(List.of(), 0, 20, 41).totalPages()).isEqualTo(3);
  }

  @Test
  void userAccountToStringHidesHash() {
    assertThat(new UserAccount("a", "A", "secret-hash", UserType.COMPANY).toString())
        .doesNotContain("secret-hash");
  }
}
