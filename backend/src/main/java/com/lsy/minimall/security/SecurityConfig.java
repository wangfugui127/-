package com.lsy.minimall.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lsy.minimall.common.ApiResponse;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
  @Bean
  org.springframework.boot.web.servlet.FilterRegistrationBean<BearerFilter> bearerRegistration(
      BearerFilter filter) {
    var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain chain(HttpSecurity http, BearerFilter filter, ObjectMapper mapper)
      throws Exception {
    return http.csrf(c -> c.disable())
        .formLogin(c -> c.disable())
        .httpBasic(c -> c.disable())
        .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            c ->
                c.requestMatchers(
                        "/actuator/health/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/internal/mock-payments/callback")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/products/**",
                        "/api/v1/categories",
                        "/api/v1/seckill-activities/**")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            c ->
                c.authenticationEntryPoint(
                        (req, res, e) -> {
                          res.setStatus(401);
                          res.setContentType("application/json;charset=UTF-8");
                          mapper.writeValue(
                              res.getWriter(), ApiResponse.error("UNAUTHENTICATED", "请先登录"));
                        })
                    .accessDeniedHandler(
                        (req, res, e) -> {
                          res.setStatus(403);
                          res.setContentType("application/json;charset=UTF-8");
                          mapper.writeValue(
                              res.getWriter(), ApiResponse.error("FORBIDDEN", "没有此操作权限"));
                        }))
        .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
