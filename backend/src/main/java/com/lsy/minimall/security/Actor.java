package com.lsy.minimall.security;

import com.lsy.minimall.common.BusinessException;
import org.springframework.security.core.context.SecurityContextHolder;

public record Actor(long id, String role) {
  public static Actor current() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || !(a.getPrincipal() instanceof Actor u))
      throw new BusinessException("UNAUTHENTICATED", "请先登录", 401);
    return u;
  }

  public static long userId() {
    return current().id();
  }
}
