package com.devluis.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.devluis.jwt.JwtValidator;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class GlobalConfig {
  private final JwtValidator jwtValidator;
  private final String corsAllowedOrigin;

  public GlobalConfig(JwtValidator jwtValidator, @Value("${cors.allowed-origin}") String corsAllowedOrigin) {
    this.jwtValidator = jwtValidator;
    this.corsAllowedOrigin = corsAllowedOrigin;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .exceptionHandling(handling -> handling
            .authenticationEntryPoint((request, response, authException) -> {
              response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
              response.setContentType("application/json");
              response.getWriter()
                  .write("{\"error\": \"No autorizado\", \"message\": \"Sesión inválida o inexistente\"}");
            }))
        .sessionManagement(managment -> managment.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .httpBasic(Customizer.withDefaults())
        .formLogin(form -> form.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/turns/**").authenticated()
            .requestMatchers("/ws-turns/**").authenticated()
            .requestMatchers("/auth/me").authenticated()
            .requestMatchers("/auth/recover-password/verify-otp").hasAuthority("ROLE_OTP_PENDING")
            .requestMatchers("/auth/recover-password/change").hasAuthority("ROLE_CHANGE_PASSWORD")
            .requestMatchers("/auth/recover-password/init").permitAll()
            // El OTP del registro: solo con el token flash que emitió
            // `init-registration-patient`, igual que su gemelo de recuperación.
            .requestMatchers("/auth/verify-otp").hasAuthority("ROLE_OTP_PENDING")
            .requestMatchers("/api/patients/**").authenticated()
            .requestMatchers("/api/doctors/change-password").authenticated()
            .requestMatchers("/api/operators/change-password").authenticated()
            // Catálogos y métricas del panel administrativo. VAN ANTES del
            // `anyRequest().permitAll()` de abajo, que es lo único que los
            // separa de quedar públicos — el mismo desfase que ya dejó
            // `/api/turns` abierto cuando el matcher decía `/turns/**`.
            //
            // Son de administración, así que la regla es autenticado y punto. Si
            // más adelante la app móvil necesita LEER especialidades para armar
            // un filtro, eso se abre con un matcher explícito para el GET, no
            // bajando toda la ruta.
            .requestMatchers("/api/specialities/**").authenticated()
            .requestMatchers("/api/holidays/**").authenticated()
            .requestMatchers("/api/block-reasons/**").authenticated()
            .requestMatchers("/api/time-off/**").authenticated()
            .requestMatchers("/api/metrics/**").authenticated()
            .anyRequest().permitAll()) // Permitimos todos los endpoints de momento
        .addFilterBefore(jwtValidator, BasicAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of(corsAllowedOrigin));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setExposedHeaders(List.of("New-Token"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
