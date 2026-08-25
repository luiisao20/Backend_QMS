package com.devluis.services;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Decides who may read ONE SPECIFIC PatientCoverage record. Same shape and
 * reasoning as {@link ClinicalAccessGuard} (imperatively invoked from
 * PatientCoverageService, not a {@code @PreAuthorize} SpEL expression) for
 * the exact same testability reason: this codebase's {@code @WebMvcTest}
 * slices run with {@code addFilters = false}, so neither
 * {@code @PreAuthorize} nor GlobalConfig's URL matchers are exercised there —
 * a per-record "is this MY OWN coverage" rule has to live somewhere plain
 * JUnit can reach it.
 *
 * <p>Policy (see apply report for full justification):
 * <ul>
 *   <li>ROLE_EMPLOYEE, ROLE_ADMIN: full access to any patient's coverage.
 *       Front-desk verifies/manages insurance at intake in this codebase —
 *       ROLE_DOCTOR is deliberately excluded, unlike the clinical tier:
 *       billing/insurance is not a treating doctor's need-to-know.</li>
 *   <li>ROLE_PATIENT: only their OWN coverage records (uuid match).</li>
 *   <li>Anyone else (including a ROLE_DOCTOR): denied.</li>
 * </ul>
 */
@Component
public class PatientCoverageAccessGuard {

  private static final String ROLE_EMPLOYEE = "ROLE_EMPLOYEE";
  private static final String ROLE_ADMIN = "ROLE_ADMIN";
  private static final String ROLE_PATIENT = "ROLE_PATIENT";

  public boolean isStaff(Authentication auth) {
    return hasAuthority(auth, ROLE_EMPLOYEE) || hasAuthority(auth, ROLE_ADMIN);
  }

  public boolean isOwner(Authentication auth, UUID patientUuid) {
    if (patientUuid == null) {
      return false;
    }
    return hasAuthority(auth, ROLE_PATIENT) && patientUuid.toString().equals(auth.getName());
  }

  public void assertCanAccessCoverage(Authentication auth, UUID patientUuid) {
    if (isStaff(auth) || isOwner(auth, patientUuid)) {
      return;
    }
    throw new RuntimeException("Error de permisos: no tienes acceso a esta cobertura");
  }

  private boolean hasAuthority(Authentication auth, String authority) {
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(authority::equals);
  }
}
