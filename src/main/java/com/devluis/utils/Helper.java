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

  /**
   * Agrega un token a la cookie
   * 
   * @param res    Response
   * @param jwt    Token
   * @param maxAge Segundos
   */
  public static void addJwtCookie(HttpServletResponse res, String jwt, long maxAge) {
    res.addHeader(HttpHeaders.SET_COOKIE, CookiesUtils.createJwtCookie(jwt, maxAge).toString());
  }

  /**
   * Elimina la cookie JWT del cliente
   * 
   * @param res Response
   */
  public static void deleteJwtCookie(HttpServletResponse res) {
    res.addHeader(HttpHeaders.SET_COOKIE, CookiesUtils.deleteJwtCookie().toString());
  }
}
