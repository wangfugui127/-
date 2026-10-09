package com.lsy.minimall.product;

import com.lsy.minimall.common.*;
import com.lsy.minimall.security.Actor;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ProductController {
  private final ProductService s;

  public ProductController(ProductService s) {
    this.s = s;
  }

  @GetMapping("/categories")
  public ApiResponse<?> categories() {
    return ApiResponse.ok(
        List.of(
            Map.of("code", "BOOK", "name", "阅读"),
            Map.of("code", "DIGITAL", "name", "数码"),
            Map.of("code", "LIFE", "name", "生活"),
            Map.of("code", "OTHER", "name", "其他")));
  }

  @GetMapping("/products")
  public ApiResponse<?> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String categoryCode,
      @RequestParam(required = false) String keyword) {
    return ApiResponse.ok(s.page(false, page, size, categoryCode, keyword, null));
  }

  @GetMapping("/products/{id}")
  public ApiResponse<?> detail(@PathVariable long id) {
    return ApiResponse.ok(s.detail(id));
  }

  @GetMapping("/admin/products")
  public ApiResponse<?> adminList(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String categoryCode,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status) {
    return ApiResponse.ok(s.page(true, page, size, categoryCode, keyword, status));
  }

  @PostMapping("/admin/products")
  public ApiResponse<?> create(@RequestBody Map<String, Object> b) {
    return ApiResponse.ok(s.create(b, Actor.userId()));
  }

  @PutMapping("/admin/products/{id}")
  public ApiResponse<?> update(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(s.update(id, b, Actor.userId()));
  }

  @PostMapping("/admin/products/{id}/status")
  public ApiResponse<?> status(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(
        s.status(id, Values.text(b.get("action")), Values.num(b.get("version")), Actor.userId()));
  }

  @PostMapping("/admin/products/{id}/stock-adjustments")
  public ApiResponse<?> adjust(
      @PathVariable long id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(s.adjust(id, b, key, Actor.userId()));
  }
}
