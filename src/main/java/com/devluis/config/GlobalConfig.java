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
            // Turn-specific staff overrides. Must precede the broad
            // "/api/turns/**" .authenticated() rule below — Spring Security
            // takes the first matching rule, so declaration order is what
            // makes these two more specific than that rule instead of being
            // silently absorbed by it.
            .requestMatchers("/api/turns/*/reassign", "/api/turns/*/staff-cancel")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers("/api/turns/patient/**").hasAnyAuthority("ROLE_DOCTOR", "ROLE_EMPLOYEE", "ROLE_ADMIN")
            // "Staff" tier: the two day-to-day operational lists (who is
            // waiting, who this patient is). GET /api/turns must come
            // before the "/api/turns/**" .authenticated() rule right below —
            // if that broader rule were declared first, it would win instead
            // and any authenticated PATIENT could read the whole staff
            // queue. POST /api/turns (a patient booking their own turn) and
            // GET /api/turns/me (a patient's own turns) are deliberately
            // NOT pulled up here: they must stay reachable by any
            // authenticated role, which is exactly what "/api/turns/**"
            // .authenticated() below already grants them. Same reasoning
            // for GET /api/patients: PatientController already narrows
            // GET /{id} and the caller's own GET/PUT /me elsewhere; this
            // only tightens the bare list, which previously had no
            // protection at all beyond the blanket rule below.
            //
            // "/api/metrics/**" joins this same tier: every MetricsController
            // endpoint is a GET, all of them read clinic-wide operational
            // numbers (turns by status, occupancy, staff performance), and
            // none of it is something a ROLE_PATIENT should see. Without an
            // explicit rule here it would fall through to the generic
            // ".anyRequest().authenticated()" at the very bottom of this
            // chain — reachable by ANY authenticated role, patients
            // included — so it must be declared (as it is, right here)
            // before that catch-all for the role restriction to apply.
            // Placed in this GET-scoped matcher rather than a new
            // standalone rule because it needs the exact same
            // hasAnyAuthority(...) set as /api/turns and /api/patients, and
            // every metrics route is a GET, so one shared rule covers all of
            // them with no risk of a later, more specific rule shadowing it
            // (nothing else in this chain matches "/api/metrics/**").
            .requestMatchers(HttpMethod.GET, "/api/turns", "/api/patients", "/api/metrics/**")
            .hasAnyAuthority("ROLE_DOCTOR", "ROLE_EMPLOYEE", "ROLE_ADMIN")
            // Clinical data (Encounter/Prescription: "historial-clinico",
            // "recetas", and their audit trail) is NOT authorized like a
            // catalog. "/me" is reachable by ANY authenticated role — it only
            // ever returns the caller's own uuid's data, same trust model as
            // GET /api/turns/me — and MUST be declared before the
            // "/api/encounters/*"/"/api/prescriptions/*" rule right below,
            // which would otherwise also match ".../me" as if "me" were a
            // numeric {id} (Ant "*" matches any single path segment).
            .requestMatchers(HttpMethod.GET, "/api/encounters/me", "/api/prescriptions/me")
            .authenticated()
            // Staff-only surface: only DOCTOR or ADMIN may reach these AT
            // ALL. ROLE_EMPLOYEE is deliberately excluded — it is front-desk
            // /scheduling staff in this codebase (see the operators/turns
            // comments elsewhere in this file), not clinical staff, and has
            // no need-to-know for a diagnosis or a prescription. This URL
            // gate only answers "is the caller clinical staff at all"; WHICH
            // specific patient's records a DOCTOR may see (only where they
            // are the treating doctor) is a per-record rule that cannot be
            // expressed as a static matcher — it is enforced in
            // EncounterService/PrescriptionService via ClinicalAccessGuard.
            // Must precede "/api/patients/**" .authenticated() below, same
            // reasoning as GET /api/patients/{id} and
            // PatientController#getPatient.
            .requestMatchers(HttpMethod.GET,
                "/api/patients/*/encounters", "/api/patients/*/prescriptions",
                "/api/encounters/*", "/api/prescriptions/*")
            .hasAnyAuthority("ROLE_DOCTOR", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/encounters", "/api/prescriptions")
            .hasAnyAuthority("ROLE_DOCTOR", "ROLE_ADMIN")
            // Encounter supports a correction PUT (treating doctor/admin
            // only, enforced again in EncounterService); Prescription does
            // NOT — no PUT matcher for it, because PrescriptionController has
            // no @PutMapping at all (immutable once issued, see the
            // Prescription entity).
            .requestMatchers(HttpMethod.PUT, "/api/encounters/*").hasAnyAuthority("ROLE_DOCTOR", "ROLE_ADMIN")
            // reportes/auditoria-hc: who read which patient's clinical data.
            // ADMIN only — the doctors being audited must never be able to
            // read their own access trail.
            .requestMatchers(HttpMethod.GET, "/api/clinical-access-logs").hasAuthority("ROLE_ADMIN")
            // PatientCoverage (Insurer/CoveragePlan are handled further down,
            // in the admin-catalogue block) is personal/billing data, not a
            // plain catalog — see the entity's own docblock. It needs its OWN
            // staff tier here instead of falling through to the generic
            // "/api/patients/**" .authenticated() rule two lines below, which
            // would otherwise let ANY authenticated role, patients included,
            // list another patient's coverage by uuid. Must precede that rule
            // for the same reason as every other "/api/patients/*/..."
            // sub-resource matcher above. ROLE_EMPLOYEE (not ROLE_DOCTOR) is
            // the staff role here, on purpose: insurance/billing is
            // front-desk work in this codebase, not clinical — a treating
            // doctor has no need-to-know for a patient's policy number (see
            // PatientCoverageAccessGuard). GET "/api/patient-coverages/me",
            // ".../me/quote" and ".../{id}" are deliberately NOT listed here:
            // "/me" and "/me/quote" are reachable by any authenticated role
            // (same idiom as /api/turns/me), and "/{id}" is guarded
            // per-record inside PatientCoverageService instead of by URL, so
            // all three safely fall through to
            // ".anyRequest().authenticated()" at the bottom of this chain.
            .requestMatchers(HttpMethod.GET, "/api/patients/*/coverages")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/patient-coverages")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/patient-coverages/{id}")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/patient-coverages/{id}")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            // finanzas/facturacion + finanzas/reclamos: same "front-desk
            // billing tier" as PatientCoverage right above (ROLE_EMPLOYEE or
            // ROLE_ADMIN). ROLE_DOCTOR is deliberately excluded throughout
            // the whole finance group — no clinical need-to-know for what a
            // patient owes or who is billed to which insurer, same
            // reasoning InvoiceAccessGuard/PatientCoverageAccessGuard
            // already document. GET "/api/invoices/me" and
            // GET "/api/invoices/{id}" are deliberately NOT listed here,
            // same idiom as PatientCoverage's own "/me"/"/{id}": any
            // authenticated role may reach them, and InvoiceService enforces
            // per-record ownership via InvoiceAccessGuard — a patient
            // fetching someone else's invoice is rejected THERE, not by a
            // URL matcher. Must precede "/api/patients/**" .authenticated()
            // below for the same reason as every other
            // "/api/patients/*/..." sub-resource matcher above.
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
            // Voiding an invoice corrects/reverses an already-issued
            // financial record rather than creating a new one — stricter
            // than ordinary billing writes on purpose, ROLE_ADMIN only (see
            // InvoiceService#voidInvoice: refused once PAID or already
            // VOID, no refund process is modelled).
            .requestMatchers(HttpMethod.PUT, "/api/invoices/*/void").hasAuthority("ROLE_ADMIN")
            // Claims have NO patient-facing route at all, unlike Invoice
            // (see ClaimController's own docblock) — every method on it
            // shares this exact tier.
            .requestMatchers(HttpMethod.POST, "/api/claims")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/claims", "/api/claims/*")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT,
                "/api/claims/*/accept", "/api/claims/*/reject", "/api/claims/*/mark-paid")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            // finanzas/contabilidad: read-only aggregate over the same three
            // resources, same billing tier. Deliberately narrower than
            // "/api/metrics/**" above (which also allows ROLE_DOCTOR) — see
            // AccountingController's own docblock for why.
            .requestMatchers(HttpMethod.GET, "/api/accounting/**")
            .hasAnyAuthority("ROLE_EMPLOYEE", "ROLE_ADMIN")
            // OJO: "/ws-turns/**" salio de esta lista a proposito. Ver el bloque
            // publico de mas abajo. Si vuelve aca, la pantalla de sala deja de
            // conectarse y el sintoma es una TV en "Sin conexion", no un 401
            // visible para nadie.
            .requestMatchers("/api/turns/**", "/auth/me", "/api/patients/**",
                "/api/doctors/change-password", "/api/operators/change-password")
            .authenticated()
            // ---- Pantalla de sala de espera: publica, deliberadamente ----
            //
            // Un televisor colgado en un hall no tiene quien se loguee. No hay
            // teclado, no hay sesion que renovar, no hay nadie mirando a las 3
            // de la manana cuando el token expira.
            //
            // Es seguro porque NINGUNO de los dos canales lleva un campo que
            // identifique a un paciente. WaitingRoomScreenDTO y TurnBoardDTO
            // llevan numero de turno, consultorio, especialidad y hora: lo mismo
            // que ya se grita en voz alta en la sala.
            //
            // El endpoint STOMP queda abierto, no los canales privados: los de
            // doctor y paciente viajan por convertAndSendToUser, que exige un
            // Principal, y una conexion anonima no lo tiene. Lo unico que un
            // anonimo puede leer es /topic/stablishment/{id}/{fecha}, que es el
            // canal que TurnService ya documenta como anonimo.
            //
            // Si alguien agrega un nombre de paciente a esos DTO, lo publica en
            // internet. La linea de defensa es el DTO, no esta regla.
            .requestMatchers(HttpMethod.GET, "/api/sala/*/pantalla").permitAll()
            // Landing publica: es la portada de una clinica, el visitante no
            // tiene sesion. Ninguna seccion expone dato de paciente: son la
            // marca, las sedes, los servicios, los medicos y cupos LIBRES.
            //
            // La seccion se valida contra lista blanca en LandingService, asi
            // que este comodin no abre lecturas arbitrarias del classpath.
            .requestMatchers(HttpMethod.GET, "/api/landing/**").permitAll()
            .requestMatchers("/ws-turns/**").permitAll()
            .requestMatchers("/auth/recover-password/verify-otp").hasAuthority("ROLE_OTP_PENDING")
            .requestMatchers("/auth/recover-password/change").hasAuthority("ROLE_CHANGE_PASSWORD")
            .requestMatchers("/auth/verify-registration-otp").hasAuthority("ROLE_OTP_PENDING")
            .requestMatchers("/auth/register-patient").hasAuthority("ROLE_PENDING_REGISTRATION")
            // "Public" tier: exactly the reads the patient booking flow uses
            // today (Flutter's ApiEndpoints, Angular's "Agendar" steps) —
            // the bare collection GET for each of the four catalog
            // resources, plus the four nested browse endpoints. Deliberately
            // NOT "/api/{resource}/{id}": no booking flow calls those
            // directly (only the admin panel does, always authenticated
            // already), so leaving them under the .authenticated() fallback
            // at the bottom costs nothing and avoids widening the public
            // surface past what was asked for.
            // "/api/holidays" joins this tier: a patient's booking calendar
            // needs to know a date is a clinic holiday BEFORE authenticating,
            // exactly like it already needs to know which schedules are
            // free. GET "/api/holidays/{id}" is deliberately NOT added here —
            // same reasoning as the other four resources below: no booking
            // flow reads a single holiday by id, only the admin panel does,
            // already authenticated.
            // "/api/schedule-templates" joins this tier too, for the exact
            // reason "administracion/horarios" exists: the clinic's weekly
            // opening hours become DATA instead of a hardcoded landing-page
            // string, and a landing page deriving "Lunes a Viernes
            // 08:00-17:00" from these rows must be able to read them before
            // any login, same as it already needs to know which schedules
            // are free or which dates are holidays. GET
            // "/api/schedule-templates/{id}" is deliberately NOT added here —
            // same restraint as every other resource in this tier: no public
            // consumer reads a single template by id, only the admin panel
            // does, already authenticated.
            .requestMatchers(HttpMethod.GET,
                "/api/services", "/api/doctors", "/api/stablishments", "/api/schedules", "/api/holidays",
                "/api/schedule-templates",
                "/api/services/{id}/doctors", "/api/services/{id}/schedules",
                "/api/stablishments/{id}/services", "/api/stablishments/{id}/doctors")
            .permitAll()
            // Branding (clinic name, logo, brand colors, public contact
            // details): unlike every other resource in this file, this one
            // is deliberately public reachable with NO client at all, not
            // just no booking-flow client — see BrandingController's own
            // docblock. A landing page that has not authenticated anyone
            // yet still needs to render the clinic's identity.
            .requestMatchers(HttpMethod.GET, "/api/branding").permitAll()
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
            // Bootstrap/session routes that must stay reachable with no
            // token at all. Required now that the fallback below stops
            // being permitAll() — otherwise nobody, including an admin,
            // could ever log back in. "/auth/logout" is included on
            // purpose: it only clears a security context and deletes a
            // cookie (nothing to protect), so gating it behind auth would
            // only break "log out" for a caller whose token already
            // expired, for zero security benefit.
            .requestMatchers(HttpMethod.POST,
                "/auth/login-patient", "/auth/login-doctor", "/auth/login-operator",
                "/auth/mobile/login-patient", "/auth/init-registration-patient",
                "/auth/recover-password/init", "/auth/logout")
            .permitAll()
            // "Admin" tier: every write on the five back-office resources
            // (stablishments, doctors, operators, services, schedules),
            // including their assignment sub-routes. Declared AFTER the
            // change-password rule above ON PURPOSE: PUT "/api/doctors/{id}"
            // and PUT "/api/operators/{id}" below would otherwise also match
            // ".../change-password" as if "change-password" were the {id} —
            // Spring Security takes the first matching rule, and the
            // change-password matcher above already claimed those two exact
            // paths, so this rule never sees them.
            .requestMatchers(HttpMethod.POST, "/api/stablishments/save", "/api/stablishments/{id}/services/{serviceId}")
            .hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/stablishments/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/stablishments/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/doctors/register",
                "/api/doctors/{id}/stablishments/{stablishmentId}", "/api/doctors/{id}/services/{serviceId}")
            .hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/doctors/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/doctors/{id}").hasAuthority("ROLE_ADMIN")
            // Operators: reads too, not just writes. Unlike doctors/
            // services/stablishments/schedules, the operator directory was
            // never on the "Public" list above — it is staff ACCOUNT data
            // (who holds ROLE_ADMIN, who holds ROLE_EMPLOYEE), not catalog
            // data a patient's booking flow or the "Staff" tier above needs.
            // BOOTSTRAP CAVEAT: this also means a brand-new, empty database
            // can never create its first admin through this endpoint — see
            // the apply report for a seed-on-first-boot strategy for fresh
            // deploys. Not fixed here: the database this runs against today
            // already has admins.
            .requestMatchers(HttpMethod.GET, "/api/operators", "/api/operators/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/operators/register",
                "/api/operators/{id}/stablishments/{stablishmentId}")
            .hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/operators/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/operators/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/services").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/services/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/services/{id}").hasAuthority("ROLE_ADMIN")
            // "precios/descuentos": PUT .../{id}/discount is a DIFFERENT,
            // longer path than PUT .../{id} right above (an extra path
            // segment), so the two rules never compete for the same
            // request — declaration order between them does not matter here,
            // unlike most other same-prefix pairs in this file, because both
            // resolve to the exact same authority anyway.
            .requestMatchers(HttpMethod.PUT, "/api/services/{id}/discount").hasAuthority("ROLE_ADMIN")
            // Same "not on the Public list" reasoning as operators above:
            // this is the admin screen that links a service to an
            // establishment, not something a patient's booking flow calls.
            .requestMatchers(HttpMethod.GET, "/api/services/{id}/stablishments").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/schedules", "/api/schedules/create", "/api/schedules/generate",
                "/api/schedules/generate-from-template")
            .hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/schedules/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/schedules/{id}").hasAuthority("ROLE_ADMIN")
            // "administracion/horarios": weekly recurring generation
            // patterns (ScheduleTemplate). Same admin-managed-catalogue
            // writes tier as every other resource in this file — see the
            // public GET rule above for why the bare list read is NOT gated
            // here.
            .requestMatchers(HttpMethod.POST, "/api/schedule-templates/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/schedule-templates/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/schedule-templates/{id}").hasAuthority("ROLE_ADMIN")
            // "consultorios": catalogo de consultorios fisicos por sede.
            // Escrituras ROLE_ADMIN, mismo tier que el resto de catalogos.
            // Es el requisito del negocio: el consultorio lo asigna un
            // administrador, un ROLE_DOCTOR no puede asignarse el suyo.
            //
            // El GET queda authenticated (cae al anyRequest() del final) y NO
            // publico: el operador lo necesita para elegir consultorio al
            // llamar un turno, y el operador no es admin. La pantalla de sala
            // no lee de aca, lee el board por su propio endpoint.
            .requestMatchers(HttpMethod.POST, "/api/consultorios").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/consultorios/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/consultorios/{id}").hasAuthority("ROLE_ADMIN")
            // "Bloqueo de citas" group (block-reasons, holidays, time-offs):
            // admin-managed catalogues, writes are ROLE_ADMIN only, same tier
            // as the five resources above. Reads are DELIBERATELY split:
            // "/api/holidays" (bare GET only) is public — see the Public
            // tier comment above. "/api/holidays/{id}", "/api/block-reasons"
            // and "/api/time-offs" have NO explicit rule here on purpose and
            // fall through to ".anyRequest().authenticated()" at the bottom
            // of this chain: any authenticated role (not just ROLE_ADMIN) can
            // read them — a doctor filling their own time-off request needs
            // the reason catalog, and TimeOff itself is HR-adjacent data with
            // no public-booking use case (unlike the fact that a date is a
            // holiday, which the calendar UI shows before login).
            .requestMatchers(HttpMethod.POST, "/api/block-reasons/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/block-reasons/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/block-reasons/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/holidays/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/holidays/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/holidays/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/time-offs/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/time-offs/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/time-offs/{id}").hasAuthority("ROLE_ADMIN")
            // Aseguradoras y planes de cobertura: admin-managed catalogues,
            // same tier as the "Bloqueo de citas" group right above (writes
            // are ROLE_ADMIN only). Reads have no explicit rule here on
            // purpose — same reasoning as "/api/block-reasons" and
            // "/api/time-offs" above: no public-booking use case today, so
            // any authenticated role falls through to
            // ".anyRequest().authenticated()" at the bottom of this chain.
            // PatientCoverage (the per-patient link) is handled separately,
            // above, with its own ROLE_EMPLOYEE-based staff tier — it is not
            // part of this catalogue group.
            .requestMatchers(HttpMethod.POST, "/api/insurers/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/insurers/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/insurers/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/coverage-plans/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/coverage-plans/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/coverage-plans/{id}").hasAuthority("ROLE_ADMIN")
            // "precios" group: paquetes (ServicePackage), sesiones
            // (SessionPlan), promociones (Promotion). Writes are ROLE_ADMIN
            // only, same catalogue-writes tier as every other admin-managed
            // resource above. Reads (bare collection GET) are PUBLIC — see
            // the Public tier above — so no read rule is needed here.
            // "precios/descuentos" has no block here at all: it is a view
            // over Servicio.discount, covered by the /api/services rules
            // above (including the new PUT .../{id}/discount sub-route).
            .requestMatchers(HttpMethod.POST, "/api/packages/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/packages/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/packages/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/session-plans/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/session-plans/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/session-plans/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/promotions/save").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/promotions/{id}").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/promotions/{id}").hasAuthority("ROLE_ADMIN")
            // Config panel: branding write, and every admin-modules
            // operation. GET /api/branding is public — see the Public tier
            // above — so only its write is here. admin-modules has NO
            // public or staff tier at all: nothing outside the admin panel
            // has a legitimate reason to know which panel destinations are
            // enabled, unlike branding, which is public identity data. See
            // AdminModuleController's own docblock for why the read is
            // ROLE_ADMIN too, not just the write.
            .requestMatchers(HttpMethod.PUT, "/api/branding").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/admin-modules").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/admin-modules/{moduleKey}").hasAuthority("ROLE_ADMIN")
            // Same hole as "/api/operators/register", reached through a
            // second, dead front door: AuthController has its own duplicate
            // "/auth/register-doctor" and "/auth/register-operator" that
            // call the exact same services, were not named in the reported
            // exploit, but were equally unauthenticated. Neither Angular nor
            // Flutter calls them — DoctorController#register and
            // OperatorController#register under "/api/" are what both
            // clients actually use.
            .requestMatchers(HttpMethod.POST, "/auth/register-doctor", "/auth/register-operator")
            .hasAuthority("ROLE_ADMIN")
            // Unauthenticated arbitrary-recipient mail relay today
            // (attacker-controlled to/subject/text through this server's own
            // SMTP credentials), zero callers in either client. Admin-gated
            // rather than removed, in case manual testing still depends on
            // it.
            .requestMatchers(HttpMethod.POST, "/dev/send-email").hasAuthority("ROLE_ADMIN")
            // Anything not explicitly listed above now requires SOME
            // authenticated session instead of being open by default. This
            // codebase has shipped a new controller open by accident before
            // (see the "/auth/register-doctor" / "/auth/register-operator"
            // duplicate front door above) — flipping the default closes
            // that whole class of mistake instead of only patching today's
            // five controllers.
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
