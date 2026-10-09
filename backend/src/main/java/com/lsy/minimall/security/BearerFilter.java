package com.lsy.minimall.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lsy.minimall.common.*;
import com.lsy.minimall.infrastructure.Db;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerFilter extends OncePerRequestFilter {
  private final Db db;
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;

  public BearerFilter(Db db, StringRedisTemplate redis, ObjectMapper mapper) {
    this.db = db;
    this.redis = redis;
    this.mapper = mapper;
  }

  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String header = req.getHeader("Authorization");
    if (header != null) {
      if (!header.startsWith("Bearer ")) {
        error(res, 401, "UNAUTHENTICATED", "登录凭证无效");
        return;
      }
      try {
        String user = redis.opsForValue().get("auth:session:" + Values.hash(header.substring(7)));
        if (user == null) {
          error(res, 401, "UNAUTHENTICATED", "登录已过期");
          return;
        }
        var row = db.row("SELECT id,role,status FROM mall_user WHERE id=?", user);
        if (row == null || !"ACTIVE".equals(row.get("status"))) {
          error(res, 401, "UNAUTHENTICATED", "账户不可用");
          return;
        }
        var actor = new Actor(Values.num(row.get("id")), Values.text(row.get("role")));
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    actor, null, List.of(new SimpleGrantedAuthority("ROLE_" + actor.role()))));
      } catch (Exception e) {
        error(res, 503, "DEPENDENCY_UNAVAILABLE", "认证服务暂不可用");
        return;
      }
    }
    chain.doFilter(req, res);
  }

  private void error(HttpServletResponse res, int status, String code, String message)
      throws IOException {
    res.setStatus(status);
    res.setContentType("application/json;charset=UTF-8");
    mapper.writeValue(res.getWriter(), ApiResponse.error(code, message));
  }
}
