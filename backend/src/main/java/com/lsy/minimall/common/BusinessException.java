package com.lsy.minimall.common;

public class BusinessException extends RuntimeException {
  public final String code;
  public final int status;

  public BusinessException(String code, String message, int status) {
    super(message);
    this.code = code;
    this.status = status;
  }

  public static BusinessException bad(String message) {
    return new BusinessException("VALIDATION_ERROR", message, 400);
  }

  public static BusinessException conflict(String code, String message) {
    return new BusinessException(code, message, 409);
  }

  public static BusinessException missing() {
    return new BusinessException("NOT_FOUND", "记录不存在或无权访问", 404);
  }
}
