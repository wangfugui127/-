package com.lsy.minimall.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-200)
public class TraceFilter extends OncePerRequestFilter {
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String id = UUID.randomUUID().toString().replace("-", "");
    MDC.put("traceId", id);
    res.setHeader("X-Trace-Id", id);
    try {
      chain.doFilter(req, res);
    } finally {
      MDC.remove("traceId");
    }
  }
}
