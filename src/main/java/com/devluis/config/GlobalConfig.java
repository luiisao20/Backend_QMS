package com.devluis.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
            // -------------------------------------------------------------
            // 1. RUTAS PÚBLICAS Y OTP
            // -------------------------------------------------------------
            .requestMatchers(HttpMethod.GET, "/api/sala/*/pantalla", "/api/landing/**", "/api/branding", 
                "/api/services", "/api/doctors", "/api/stablishments", "/api/schedules", "/api/holidays", 
                "/api/schedule-templates", "/api/services/*/doctors", "/api/services/*/schedules", 
                "/api/stablishments/*/services", "/api/stablishments/*/doctors", "/api/packages", 
                "/api/session-plans", "/api/promotions").permitAll()
            .requestMatchers("/ws-turns/**", "/api/ai/chat", "/auth/login-patient", "/auth/login-doctor", "/auth/login-operator", "/auth/mobile/login-patient", 
                "/auth/init-registration-patient", "/auth/recover-password/init", "/auth/logout").permitAll()
            .requestMatchers("/auth/recover-password/verify-otp", "/auth/verify-registration-otp").hasAuthority("ROLE_OTP_PENDING")
            .requestMatchers("/auth/recover-password/change").hasAuthority("ROLE_CHANGE_PASSWORD")
            .requestMatchers("/auth/register-patient").hasAuthority("ROLE_PENDING_REGISTRATION")

            // -------------------------------------------------------------
            // 2. RUTAS "ME" AUTHENTICATED (Debe ir antes de los comodines /*)
            // -------------------------------------------------------------
            .requestMatchers(HttpMethod.GET, "/api/encounters/me", "/api/prescriptions/me", 
                "/api/patient-coverages/me", "/api/patient-coverages/me/quote", 
                "/api/invoices/me", "/api/turns/me", "/api/patients/me").authenticated()
            .requestMatchers(HttpMethod.PUT, "/api/patients/me").authenticated()
            .requestMatchers(HttpMethod.GET, "/api/doctors/me/invoices").hasAuthority("ROLE_DOCTOR")
            .requestMatchers("/auth/me", "/api/patients/change-password", "/api/doctors/change-password", "/api/operators/change-password").authenticated()

            // -------------------------------------------------------------
            // 3. GRUPO 1: ROLE_DOCTOR, ROLE_EMPLOYEE, ROLE_ADMIN
            // -------------------------------------------------------------
            .requestMatchers(
                "/api/turns/patient/**", 
                "/api/turns", 
                "/api/patients", 
                "/api/patients/*",
                "/api/metrics/**",
                "/api/patients/*/clinical-summary",
                "/api/encounters",
                "/api/encounters/*",
                "/api/patients/*/encounters",
                "/api/invoices",
                "/api/prescriptions",
                "/api/prescriptions/*",
                "/api/patients/*/prescriptions"
            ).hasAnyAuthority("ROLE_DOCTOR", "ROLE_EMPLOYEE", "ROLE_ADMIN")

            // -------------------------------------------------------------
            // 4. GRUPO 2: ROLE_EMPLOYEE, ROLE_ADMIN
            // -------------------------------------------------------------
            .requestMatchers(
                "/api/turns/*/reassign", "/api/turns/*/staff-cancel", "/api/turns/*/treated/admin",
                "/api/turns/*/waiting", "/api/turns/*/in-treatment",
                "/api/patients/*/coverages", "/api/patient-coverages", "/api/patient-coverages/*",
                "/api/patients/*/invoices",
                "/api/invoices/*/payments",
                "/api/claims", "/api/claims/*", "/api/claims/*/accept", "/api/claims/*/reject", "/api/claims/*/mark-paid",
                "/api/accounting/**"
            ).hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")

            // -------------------------------------------------------------
            // 5. REGLAS ESPECÍFICAS ADICIONALES (Administración)
            // -------------------------------------------------------------
            .requestMatchers(HttpMethod.PUT, "/api/invoices/*/void").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/clinical-access-logs").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/stablishments/save", "/api/stablishments/*/services/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/stablishments/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/stablishments/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/doctors/register", "/api/doctors/*/stablishments/*", "/api/doctors/*/services/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/doctors/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/doctors/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/operators", "/api/operators/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/operators/register", "/api/operators/*/stablishments/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/operators/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/operators/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/services").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/services/*", "/api/services/*/discount").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/services/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/services/*/stablishments").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/schedules", "/api/schedules/create", "/api/schedules/generate", "/api/schedules/generate-from-template").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/schedules/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/schedules/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/schedule-templates/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/schedule-templates/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/schedule-templates/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/consultorios").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/consultorios/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/consultorios/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/block-reasons/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/block-reasons/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/block-reasons/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/holidays/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/holidays/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/holidays/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/time-offs/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/time-offs/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/time-offs/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/insurers/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/insurers/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/insurers/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/coverage-plans/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/coverage-plans/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/coverage-plans/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/packages/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/packages/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/packages/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/session-plans/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/session-plans/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/session-plans/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/promotions/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/promotions/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/promotions/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/branding").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/admin-modules").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/admin-modules/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/auth/register-doctor", "/auth/register-operator").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/dev/send-email").hasAuthority("ROLE_ADMIN")
            .anyRequest().authenticated())
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
