package com.lsy.minimall.auth;

import com.lsy.minimall.common.*;
import com.lsy.minimall.security.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }
// 定义数据结构
  public record Credentials(@NotBlank String username, @NotBlank String password) {}

  @PostMapping("/register")
  public ApiResponse<?> register(@Valid @RequestBody Credentials body) {
    return ApiResponse.ok(service.register(body.username(), body.password()));
  }

  @PostMapping("/login")
  public ApiResponse<?> login(@Valid @RequestBody Credentials body) {
    return ApiResponse.ok(service.login(body.username(), body.password()));
  }

  @PostMapping("/logout")
  public ApiResponse<?> logout(@RequestHeader("Authorization") String header) {
    service.logout(header);
    return ApiResponse.ok(null);
  }

  @GetMapping("/me")
  public ApiResponse<?> me() {
    return ApiResponse.ok(service.me(Actor.userId()));
  }
}
