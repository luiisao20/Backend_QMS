package com.devluis.utils;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

@Component
public class Helper {
  public static ResponseEntity<?> getResponseMessage(String msg, HttpStatus status) {
    return ResponseEntity.status(status).body(Map.of("message", msg));
  }

  public static void addJwtCookie(HttpServletResponse res, String jwt, long maxAge) {
    res.addHeader(HttpHeaders.SET_COOKIE, CookiesUtils.createJwtCookie(jwt, maxAge).toString());
  }
}
