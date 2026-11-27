package edu.gatech.erms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds the authenticated username to the MDC ({@code user}) for audit-friendly logs. Registered
 * inside the security filter chain right after bearer-token authentication (not as a bean).
 */
class MdcUserFilter extends OncePerRequestFilter {

  static final String MDC_KEY = "user";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    boolean added = false;
    if (authentication != null && authentication.isAuthenticated()) {
      MDC.put(MDC_KEY, authentication.getName());
      added = true;
    }
    try {
      chain.doFilter(request, response);
    } finally {
      if (added) {
        MDC.remove(MDC_KEY);
      }
    }
  }
}
