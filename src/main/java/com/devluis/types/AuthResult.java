package com.devluis.types;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthResult<T> {
  private boolean success;
  private T data;
  private String message;
  private HttpStatus status;

  public static <T> AuthResult<T> ok(T data) {
    return AuthResult.<T>builder()
        .success(true)
        .data(data)
        .status(HttpStatus.OK)
        .build();
  }

  public static <T> AuthResult<T> error(String message, HttpStatus status) {
    return AuthResult.<T>builder()
        .success(false)
        .message(message)
        .status(status)
        .build();
  }
}
