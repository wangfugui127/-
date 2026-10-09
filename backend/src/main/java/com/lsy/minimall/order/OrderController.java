package com.lsy.minimall.order;

import com.lsy.minimall.common.*;
import com.lsy.minimall.security.Actor;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
  private final OrderService s;

  public OrderController(OrderService s) {
    this.s = s;
  }

  @PostMapping("/orders")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<?> create(
      @RequestHeader("Idempotency-Key") String key, @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(s.create(Actor.userId(), key, b));
  }

  @GetMapping("/orders")
  public ApiResponse<?> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String status) {
    return ApiResponse.ok(s.page(Actor.userId(), false, page, size, status, null, null));
  }

  @GetMapping("/orders/{id}")
  public ApiResponse<?> detail(@PathVariable long id) {
    return ApiResponse.ok(s.detail(id, Actor.userId(), false));
  }

  @PostMapping("/orders/{id}/cancel")
  public ApiResponse<?> cancel(
      @PathVariable long id, @RequestBody(required = false) Map<String, Object> b) {
    return ApiResponse.ok(
        s.cancel(
            id,
            Actor.userId(),
            b == null ? "用户取消" : Values.required(b.getOrDefault("reason", "用户取消"), 128)));
  }

  @PostMapping("/orders/{id}/confirm")
  public ApiResponse<?> confirm(@PathVariable long id) {
    return ApiResponse.ok(s.confirm(id, Actor.userId()));
  }

  @GetMapping("/admin/orders")
  public ApiResponse<?> adminList(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String orderNo,
      @RequestParam(required = false) String userId) {
    return ApiResponse.ok(s.page(Actor.userId(), true, page, size, status, orderNo, userId));
  }

  @GetMapping("/admin/orders/{id}")
  public ApiResponse<?> adminDetail(@PathVariable long id) {
    return ApiResponse.ok(s.detail(id, Actor.userId(), true));
  }

  @PostMapping("/admin/orders/{id}/shipments")
  public ApiResponse<?> ship(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(s.ship(id, Actor.userId(), b));
  }
}
