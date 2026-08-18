package com.devluis.jwt;

import java.io.IOException;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtValidator extends OncePerRequestFilter {
  private String recoverToken(HttpServletRequest req) {
    if (req.getCookies() != null) {
      for (Cookie cookie : req.getCookies()) {
        if ("jwt".equals(cookie.getName())) {
          return cookie.getValue();
        }
      }
    }
    return null;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    String jwt = recoverToken(request);

    // Si existe el token, entonces pasa a verificar la autenticacion
    if (jwt != null) {
      try {

        SecretKey key = Keys.hmacShaKeyFor(JwtConstants.getSecretKey().getBytes());
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(jwt).getBody();

        // Extrae los datos del token y procede a autenticar al usuario
        String email = String.valueOf(claims.get("email"));
        String authorities = String.valueOf(claims.get("authorities"));
        List<GrantedAuthority> auth = AuthorityUtils.commaSeparatedStringToAuthorityList(authorities);

        Authentication authentication = new UsernamePasswordAuthenticationToken(email, null, auth);
        SecurityContextHolder.getContext().setAuthentication(authentication);

      } catch (Exception e) {
        // Permitir logout aunque el token esté expirado,
        // para que el controller pueda limpiar la cookie del navegador
        if ("/auth/logout".equals(request.getServletPath())) {
          filterChain.doFilter(request, response);
          return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("Token invalido o sesion expirada");
        return;
      }
    }

    filterChain.doFilter(request, response);
  }
}
