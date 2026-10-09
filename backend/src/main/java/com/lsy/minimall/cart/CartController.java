package com.lsy.minimall.cart;

import com.lsy.minimall.common.*;
import com.lsy.minimall.security.Actor;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
  private final CartService s;

  public CartController(CartService s) {
    this.s = s;
  }

  @GetMapping
  public ApiResponse<?> list() {
    return ApiResponse.ok(s.list(Actor.userId()));
  }

  @PostMapping("/items")
  public ApiResponse<?> add(@RequestBody Map<String, Object> b) {
    return ApiResponse.ok(
        s.add(
            Actor.userId(),
            Values.num(b.get("productId")),
            Math.toIntExact(Values.num(b.get("quantity")))));
  }

  @PatchMapping("/items/{id}")
  public ApiResponse<?> change(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return ApiResponse.ok(
        s.change(Actor.userId(), id, Math.toIntExact(Values.num(b.get("quantity")))));
  }

  @DeleteMapping("/items/{id}")
  public ApiResponse<?> remove(@PathVariable long id) {
    s.remove(Actor.userId(), id);
    return ApiResponse.ok(null);
  }
}
