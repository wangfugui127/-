package com.lsy.minimall.seckill;

import com.lsy.minimall.common.*;
import com.lsy.minimall.security.Actor;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ActivityController {
  private final ActivityService s;

  public ActivityController(ActivityService s) {
    this.s = s;
  }

  @GetMapping("/seckill-activities")
  public ApiResponse<?> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String status) {
    return ApiResponse.ok(s.page(false, page, size, status));
  }

  @GetMapping("/seckill-activities/{id}")
  public ApiResponse<?> detail(@PathVariable long id) {
    Long user = null;
    try {
      user = Actor.userId();
    } catch (BusinessException ignored) {
    }
    return ApiResponse.ok(s.detail(id, user));
  }

  @PostMapping("/seckill-activities/{id}/orders")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<?> buy(
      @PathVariable long id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(s.buy(id, Actor.userId(), key, b));
  }

  @GetMapping("/admin/seckill-activities")
  public ApiResponse<?> adminList(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String status) {
    return ApiResponse.ok(s.page(true, page, size, status));
  }

  @PostMapping("/admin/seckill-activities")
  public ApiResponse<?> create(@RequestBody Map<String, Object> b) {
    return ApiResponse.ok(s.create(b, Actor.userId()));
  }

  @PutMapping("/admin/seckill-activities/{id}")
  public ApiResponse<?> edit(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(s.edit(id, b, Actor.userId()));
  }

  @PostMapping("/admin/seckill-activities/{id}/status")
  public ApiResponse<?> action(
      @PathVariable long id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(s.action(id, Actor.userId(), key, b));
  }
}
