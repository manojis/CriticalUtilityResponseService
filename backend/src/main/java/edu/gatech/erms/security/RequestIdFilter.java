package edu.gatech.erms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Correlates logs and responses: reuses a well-formed incoming {@code X-Request-Id} or generates a
 * UUID, stores it in the MDC under {@code requestId} and echoes it in the response header. Runs
 * before Spring Security so 401/403 responses carry the id too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Request-Id";
  public static final String MDC_KEY = "requestId";

  /** Accept only short, log-safe ids from clients (prevents log injection). */
  private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String incoming = request.getHeader(HEADER);
    String requestId =
        incoming != null && SAFE_ID.matcher(incoming).matches()
            ? incoming
            : UUID.randomUUID().toString();
    MDC.put(MDC_KEY, requestId);
    response.setHeader(HEADER, requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }
}
