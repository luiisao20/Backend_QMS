package com.devluis.jwt;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {
  public static String generateToken(Authentication auth) {
    Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
    String roles = populateAuthorities(authorities);
    SecretKey key = Keys.hmacShaKeyFor(JwtConstants.SECRET_KEY_STATIC.getBytes());

    return Jwts.builder()
        .setIssuedAt(new Date())
        .setExpiration(new Date(new Date().getTime() + 86400000))
        .claim("uuid", auth.getName())
        .claim("authorities", roles)
        .signWith(key)
        .compact();
  }

  private static String populateAuthorities(Collection<? extends GrantedAuthority> authorities) {
    Set<String> auths = new HashSet<>();
    for (GrantedAuthority grantedAuthority : authorities) {
      auths.add(grantedAuthority.getAuthority());
    }
    return String.join(",", auths);
  }
}
