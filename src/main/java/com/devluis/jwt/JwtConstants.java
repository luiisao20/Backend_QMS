package com.devluis.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class JwtConstants {
  public static final String JWT_HEADER = "Authorization";

  public static String SECRET_KEY_STATIC;

  @Value("${spring.jwt.secret}")
  private String secretKey;

  @PostConstruct
  public void init() {
    SECRET_KEY_STATIC = secretKey;
  }

  public static String getSecretKey() {
    return SECRET_KEY_STATIC;
  }
}
