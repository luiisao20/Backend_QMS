package com.devluis.utils;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;

@Component
public class CookiesUtils {

  /**
   * Política SameSite de la cookie JWT, configurable por entorno con la
   * propiedad app.cookie.same-site:
   * - "Lax": el navegador no adjunta la cookie en peticiones cross-site
   * (mitigación CSRF a nivel navegador). Requiere que frontend y API
   * compartan sitio (mismo eTLD+1).
   * - "None": necesario si el frontend vive en otro sitio (ej. otro dominio);
   * en ese caso la protección CSRF por token es la única barrera.
   */
  private static String sameSite;

  /**
   * Flag Secure de la cookie JWT, configurable con app.cookie.secure.
   * En desarrollo local (http://localhost) debe ser false: Postman y algunos
   * clientes no envían cookies Secure sobre HTTP. En producción siempre true.
   */
  private static boolean secure;

  public CookiesUtils(
      @Value("${app.cookie.same-site:Lax}") String sameSite,
      @Value("${app.cookie.secure:true}") boolean secure) {
    CookiesUtils.sameSite = sameSite;
    CookiesUtils.secure = secure;
  }

  /**
   * Extrae el token de las cookies del navegador
   * 
   * @param cookies
   * @return
   */
  public static String extractTokenFromCookies(Cookie[] cookies) {
    if (cookies == null)
      return null;

    return Arrays.stream(cookies)
        .filter(c -> "jwt".equals(c.getName()))
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
  }

  /**
   * Setea el token en las cookies del navegador
   * 
   * @param jwt
   * @param maxAge
   * @return
   */
  public static ResponseCookie createJwtCookie(String jwt, long maxAge) {
    return ResponseCookie.from("jwt", jwt)
        .httpOnly(true)
        .secure(secure)
        .path("/")
        .maxAge(maxAge)
        .sameSite(sameSite)
        .build();
  }

  /**
   * Borra el token de las cookies del navegador
   *
   * @return
   */
  public static ResponseCookie deleteJwtCookie() {
    return ResponseCookie.from("jwt", "")
        .httpOnly(true)
        .secure(secure)
        .path("/")
        .maxAge(0)
        .sameSite(sameSite)
        .build();
  }
}
