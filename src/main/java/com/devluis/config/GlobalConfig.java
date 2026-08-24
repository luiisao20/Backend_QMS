package com.devluis.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
@EnableMethodSecurity
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
            .requestMatchers("/api/turns/patient/**").hasAnyAuthority("ROLE_DOCTOR", "ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers("/api/turns/**").authenticated()
            .requestMatchers("/ws-turns/**").authenticated()
            .requestMatchers("/auth/me").authenticated()
            .requestMatchers("/auth/recover-password/verify-otp").hasAuthority("ROLE_OTP_PENDING")
            .requestMatchers("/auth/recover-password/change").hasAuthority("ROLE_CHANGE_PASSWORD")
            .requestMatchers("/auth/recover-password/init").permitAll()
            .requestMatchers("/api/patients/**").authenticated()
            .requestMatchers("/api/doctors/change-password").authenticated()
            .requestMatchers("/api/operators/change-password").authenticated()
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
