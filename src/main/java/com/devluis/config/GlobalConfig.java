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
            .requestMatchers("/api/turns/*/reassign", "/api/turns/*/staff-cancel", "/api/turns/*/treated/admin")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers("/api/turns/patient/**").hasAnyAuthority("ROLE_DOCTOR", "ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/turns", "/api/patients", "/api/metrics/**")
            .hasAnyAuthority("ROLE_DOCTOR", "ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/encounters/me", "/api/prescriptions/me")
            .authenticated()
            .requestMatchers(HttpMethod.GET,
                "/api/patients/*/encounters", "/api/patients/*/prescriptions",
                "/api/encounters/*", "/api/prescriptions/*")
            .hasAnyAuthority("ROLE_DOCTOR", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/encounters", "/api/prescriptions")
            .hasAnyAuthority("ROLE_DOCTOR", "ROLE_ADMIN")
            // El resumen de historia clinica con IA. Va en este bloque, y no
            // mas abajo, porque es una LECTURA clinica: ClinicalSummaryService
            // pide el historial y las recetas de ESTE paciente por los mismos
            // servicios que la pantalla "historial-clinico", asi que hereda su
            // regla fina ("este doctor trato a este paciente", via
            // ClinicalAccessGuard) y deja el mismo asiento en
            // ClinicalAccessLog. No alcanzaba con caer al
            // ".anyRequest().authenticated()" del final: eso dejaria entrar a
            // ROLE_EMPLOYEE y ROLE_PATIENT al nivel de ruta, confiando solo en
            // el @PreAuthorize del controller. Para datos clinicos, las dos
            // capas.
            //
            // Es POST y no GET aunque no escriba en la base: cada llamada
            // gasta tokens de un proveedor externo y no es idempotente. Un GET
            // invita a que el navegador o un proxy la repitan solo.
            .requestMatchers(HttpMethod.POST, "/api/patients/*/clinical-summary")
            .hasAnyAuthority("ROLE_DOCTOR", "ROLE_ADMIN")
            // Encounter supports a correction PUT (treating doctor/admin
            // only, enforced again in EncounterService); Prescription does
            // NOT — no PUT matcher for it, because PrescriptionController has
            // no @PutMapping at all (immutable once issued, see the
            // Prescription entity).
            .requestMatchers(HttpMethod.PUT, "/api/encounters/*").hasAnyAuthority("ROLE_DOCTOR", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/clinical-access-logs").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/patients/*/coverages")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/patient-coverages")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/patient-coverages/{id}")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/patient-coverages/{id}")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/invoices")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN", "ROLE_DOCTOR")
            .requestMatchers(HttpMethod.GET, "/api/invoices", "/api/patients/*/invoices")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/doctors/me/invoices")
            .hasAuthority("ROLE_DOCTOR")
            .requestMatchers(HttpMethod.POST, "/api/invoices/*/payments")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/invoices/*/payments")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN", "ROLE_DOCTOR")
            .requestMatchers(HttpMethod.PUT, "/api/invoices/*/void").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/claims")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/claims", "/api/claims/*")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT,
                "/api/claims/*/accept", "/api/claims/*/reject", "/api/claims/*/mark-paid")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/accounting/**")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers("/api/turns/**", "/auth/me", "/api/patients/**",
                "/api/doctors/change-password", "/api/operators/change-password")
            .authenticated()
            .requestMatchers(HttpMethod.GET, "/api/sala/*/pantalla").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/landing/**").permitAll()
            .requestMatchers("/ws-turns/**").permitAll()
            .requestMatchers("/auth/recover-password/verify-otp").hasAuthority("ROLE_OTP_PENDING")
            .requestMatchers("/auth/recover-password/change").hasAuthority("ROLE_CHANGE_PASSWORD")
            .requestMatchers("/auth/verify-registration-otp").hasAuthority("ROLE_OTP_PENDING")
            .requestMatchers("/auth/register-patient").hasAuthority("ROLE_PENDING_REGISTRATION")
            .requestMatchers(HttpMethod.GET,
                "/api/services", "/api/doctors", "/api/stablishments", "/api/schedules", "/api/holidays",
                "/api/schedule-templates",
                "/api/services/{id}/doctors", "/api/services/{id}/schedules",
                "/api/stablishments/{id}/services", "/api/stablishments/{id}/doctors")
            .permitAll()
            .requestMatchers(HttpMethod.GET, "/api/branding").permitAll()
            // El chat de atencion al cliente de la landing. Publico por la
            // misma razon que GET /api/branding: lo usa un visitante que
            // todavia no se registro, y sin esta regla caeria al
            // ".anyRequest().authenticated()" del final y pediria login para
            // lo unico que sabe hacer — contestar lo que ya esta publicado.
            //
            // El agente de n8n detras solo consulta endpoints que YA son
            // permitAll() (doctores, agenda, sedes, servicios), asi que no
            // abre acceso a nada nuevo. Pero es el unico POST anonimo del
            // sistema que gasta tokens de un proveedor externo, asi que
            // AiController#chat lo pasa por ChatRateLimiter antes de llamar a
            // n8n: el riesgo aqui no es fuga de datos, es que alguien agote la
            // cuota y el chat deje de andar para los pacientes reales.
            .requestMatchers(HttpMethod.POST, "/api/ai/chat").permitAll()
            // "precios" group (paquetes/sesiones/promociones): a prospective
            // patient legitimately needs to see bundle/session/promotion
            // prices BEFORE logging in to decide whether to book — same
            // reasoning as GET /api/services above. Split into its own
            // matcher (instead of folding into the block above) so that
            // block's own "four catalog resources" comment stays accurate.
            // Bare collection GET only, same "not /{id}" restraint as every
            // other entry in the Public tier: no booking flow reads a single
            // package/plan/promotion by id today, only the admin panel does,
            // already authenticated. "precios/descuentos" needs no entry
            // here at all — it is a view over Servicio.discount, already
            // covered by GET /api/services above (see apply report).
            .requestMatchers(HttpMethod.GET, "/api/packages", "/api/session-plans", "/api/promotions")
            .permitAll()
            .requestMatchers(HttpMethod.POST,
                "/auth/login-patient", "/auth/login-doctor", "/auth/login-operator",
                "/auth/mobile/login-patient", "/auth/init-registration-patient",
                "/auth/recover-password/init", "/auth/logout")
            .permitAll()
            .requestMatchers(HttpMethod.POST, "/api/stablishments/save", "/api/stablishments/{id}/services/{serviceId}")
            .hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/stablishments/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/stablishments/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/doctors/register",
                "/api/doctors/{id}/stablishments/{stablishmentId}", "/api/doctors/{id}/services/{serviceId}")
            .hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/doctors/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/doctors/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/operators", "/api/operators/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/operators/register",
                "/api/operators/{id}/stablishments/{stablishmentId}")
            .hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/operators/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/operators/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/services").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/services/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/services/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/services/{id}/discount").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/services/{id}/stablishments").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/schedules", "/api/schedules/create", "/api/schedules/generate",
                "/api/schedules/generate-from-template")
            .hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/schedules/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/schedules/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/schedule-templates/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/schedule-templates/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/schedule-templates/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/consultorios").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/consultorios/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/consultorios/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/block-reasons/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/block-reasons/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/block-reasons/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/holidays/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/holidays/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/holidays/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/time-offs/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/time-offs/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/time-offs/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/insurers/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/insurers/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/insurers/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/coverage-plans/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/coverage-plans/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/coverage-plans/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/packages/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/packages/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/packages/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/session-plans/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/session-plans/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/session-plans/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/promotions/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/promotions/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/promotions/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/branding").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/admin-modules").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/admin-modules/{moduleKey}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/auth/register-doctor", "/auth/register-operator")
            .hasAuthority("ROLE_ADMIN")
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
