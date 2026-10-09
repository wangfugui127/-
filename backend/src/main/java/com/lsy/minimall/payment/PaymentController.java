package com.lsy.minimall.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lsy.minimall.common.*;
import com.lsy.minimall.security.Actor;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
  private final PaymentService s;
  private final ObjectMapper mapper;

  public PaymentController(PaymentService s, ObjectMapper mapper) {
    this.s = s;
    this.mapper = mapper;
  }

  @PostMapping("/orders/{id}/mock-payments")
  public ApiResponse<?> create(
      @PathVariable long id, @RequestBody(required = false) Map<String, Object> b) {
    return ApiResponse.ok(
        s.create(
            id,
            Actor.userId(),
            b == null ? "SUCCESS" : Values.text(b.getOrDefault("scenario", "SUCCESS"))));
  }

  @GetMapping("/payments/{id}")
  public ApiResponse<?> detail(@PathVariable long id) {
    return ApiResponse.ok(s.detail(id, Actor.userId()));
  }

  @PostMapping("/internal/mock-payments/callback")
  public ApiResponse<?> callback(
      @RequestHeader("X-Mock-Timestamp") String timestamp,
      @RequestHeader("X-Mock-Signature") String signature,
      @RequestBody String body)
      throws Exception {
    s.verify(timestamp, signature, body);
    return ApiResponse.ok(
        s.callback(
            mapper.readValue(
                body,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {})));
  }
}
