package com.lsy.minimall.common;

import org.slf4j.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<?> method(Exception e) {
    return ResponseEntity.status(405).body(ApiResponse.error("METHOD_NOT_ALLOWED", "请求方法不受支持"));
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  public ResponseEntity<?> missing(Exception e) {
    return ResponseEntity.status(404).body(ApiResponse.error("NOT_FOUND", "资源不存在"));
  }

  private final Logger log = LoggerFactory.getLogger(Errors.class);

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<?> business(BusinessException e) {
    return ResponseEntity.status(e.status).body(ApiResponse.error(e.code, e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest()
        .body(
            ApiResponse.error(
                "VALIDATION_ERROR",
                e.getBindingResult().getFieldErrors().stream()
                    .map(f -> f.getField() + ": " + f.getDefaultMessage())
                    .findFirst()
                    .orElse("参数错误")));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.bind.MissingRequestHeaderException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<?> bad(Exception e) {
    return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", "请求格式或参数不正确"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> unexpected(Exception e) {
    log.error("Request failed", e);
    return ResponseEntity.status(e instanceof DataAccessException ? 503 : 500)
        .body(
            ApiResponse.error(
                e instanceof DataAccessException ? "DEPENDENCY_UNAVAILABLE" : "INTERNAL_ERROR",
                "操作暂时无法完成，请保留请求键重试"));
  }
}
