package com.lsy.minimall.common;

import java.util.*;
import org.slf4j.MDC;

public record ApiResponse<T>(String code, String message, T data, String traceId) {
  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>("OK", "success", data, MDC.get("traceId"));
  }

  public static ApiResponse<Object> error(String code, String message) {
    return new ApiResponse<>(code, message, null, MDC.get("traceId"));
  }
}
