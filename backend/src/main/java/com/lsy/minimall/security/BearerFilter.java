package com.lsy.minimall.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lsy.minimall.auth.AuthService;
import com.lsy.minimall.common.*;
import com.lsy.minimall.infrastructure.*;
import com.lsy.minimall.persistence.mapper.*;
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
  private final MallUserMapper users;
  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;

  public BearerFilter(MallUserMapper users, StringRedisTemplate redis, ObjectMapper mapper) {
    this.users = users;
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
        String sessionKey = "auth:session:" + Values.hash(header.substring(7));
        String user = redis.opsForValue().get(sessionKey);
        if (user == null) {
          error(res, 401, "UNAUTHENTICATED", "登录已过期");
          return;
        }
        var row = users.findIdentityById(user);
        if (row == null || !"ACTIVE".equals(row.get("status"))) {
          error(res, 401, "UNAUTHENTICATED", "账户不可用");
          return;
        }
        // 认证通过后滑动续期；EXPIRE 不会重建已过期或已退出删除的会话。
        if (!Boolean.TRUE.equals(redis.expire(sessionKey, AuthService.SESSION_TTL))) {
          error(res, 401, "UNAUTHENTICATED", "登录已过期");
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
