package edu.gatech.erms.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @Test
  void echoesWellFormedIncomingIdAndExposesItInMdc() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "abc-123");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> seen = new AtomicReference<>();

    filter.doFilter(request, response, (req, res) -> seen.set(MDC.get("requestId")));

    assertThat(response.getHeader("X-Request-Id")).isEqualTo("abc-123");
    assertThat(seen.get()).isEqualTo("abc-123");
    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void replacesMissingOrUnsafeIds() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "bad\nid");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader("X-Request-Id"))
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  }
}
