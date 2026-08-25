package com.devluis.services;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Decides who may read ONE SPECIFIC Invoice. Same shape and reasoning as
 * {@link PatientCoverageAccessGuard} (imperatively invoked from
 * InvoiceService, not a {@code @PreAuthorize} SpEL expression): this
 * codebase's {@code @WebMvcTest} slices run with {@code addFilters = false},
 * so a per-record "is this MY OWN invoice" rule has to live somewhere plain
 * JUnit can reach it.
 *
 * <p>Policy (money is closer to coverage than to clinical — see the apply
 * report):
 * <ul>
 *   <li>ROLE_EMPLOYEE, ROLE_ADMIN: full access to any patient's invoices.
 *       Billing/insurance is front-desk work in this codebase, same tier as
 *       PatientCoverage — ROLE_DOCTOR is deliberately excluded, same
 *       reasoning as PatientCoverageAccessGuard (no clinical need-to-know for
 *       what a patient owes).</li>
 *   <li>ROLE_PATIENT: only their OWN invoices (uuid match).</li>
 *   <li>Anyone else (including a ROLE_DOCTOR): denied.</li>
 * </ul>
 */
@Component
public class InvoiceAccessGuard {

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

  public void assertCanAccessInvoice(Authentication auth, UUID patientUuid) {
    if (isStaff(auth) || isOwner(auth, patientUuid)) {
      return;
    }
    throw new RuntimeException("Error de permisos: no tienes acceso a esta factura");
  }

  private boolean hasAuthority(Authentication auth, String authority) {
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(authority::equals);
  }
}
