package com.devluis.services;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Decides who may read or write ONE SPECIFIC clinical record (an Encounter or
 * a Prescription), given who is asking.
 *
 * Deliberately a plain injectable bean invoked IMPERATIVELY from
 * EncounterService/PrescriptionService, not a SpEL expression wired into
 * {@code @PreAuthorize} on the controller. Reason: this codebase's
 * {@code @WebMvcTest} slices never load {@code @EnableMethodSecurity} (see the
 * docblocks on TurnControllerTest/PatientControllerTest) — a fine-grained
 * "is this the treating doctor of THIS record" rule expressed only as
 * {@code @PreAuthorize} would be structurally untestable here. Coarse role
 * gating (can this principal reach the endpoint AT ALL) still uses
 * {@code @PreAuthorize} at the controller, mirroring
 * {@code PatientController#getPatient}; this class is the fine-grained
 * "which record" layer underneath it, and is exactly what the task asked to
 * prefer for record-dependent rules.
 *
 * Policy (see apply report for full justification):
 * - ROLE_ADMIN: full access to every clinical record.
 * - ROLE_DOCTOR: only records where they are the TREATING doctor of that
 *   specific Encounter (the doctor on the Turn's Schedule). A doctor who is
 *   NOT the treating doctor of a record gets nothing for that record — this
 *   system has no modeled notion of inter-specialist referral/consent, so
 *   "no access without being the treating doctor" is the safe default
 *   instead of inventing an implied-consent model nobody asked for.
 * - ROLE_EMPLOYEE and ROLE_PATIENT (via the staff routes — patients read
 *   their own data through the separate "/me" routes instead): denied.
 *   ROLE_EMPLOYEE is front-desk/scheduling staff in this codebase (see
 *   GlobalConfig's own comments), not clinical staff, and clinical notes are
 *   not "need to know" for scheduling.
 */
@Component
public class ClinicalAccessGuard {

  private static final String ROLE_ADMIN = "ROLE_ADMIN";
  private static final String ROLE_DOCTOR = "ROLE_DOCTOR";

  public boolean isAdmin(Authentication auth) {
    return hasAuthority(auth, ROLE_ADMIN);
  }

  public boolean isTreatingDoctor(Authentication auth, UUID treatingDoctorUuid) {
    if (treatingDoctorUuid == null) {
      return false;
    }
    return hasAuthority(auth, ROLE_DOCTOR) && treatingDoctorUuid.toString().equals(auth.getName());
  }

  /**
   * Guards read/write access to ONE record (an Encounter, or by extension a
   * Prescription via its parent Encounter's treating doctor).
   */
  public void assertCanAccessEncounter(Authentication auth, UUID treatingDoctorUuid) {
    if (isAdmin(auth) || isTreatingDoctor(auth, treatingDoctorUuid)) {
      return;
    }
    throw new RuntimeException("Error de permisos: no tienes acceso a esta historia clínica");
  }

  /**
   * For LIST endpoints ("this patient's history"): admin sees everyone's
   * records (returns null = no filter), a doctor is scoped to their OWN
   * encounters with that patient (returns their own uuid as the required
   * filter), anyone else is rejected outright — list endpoints are staff-only
   * (patients use the separate "/me" routes, which never call this method).
   */
  public UUID resolveDoctorFilter(Authentication auth) {
    if (isAdmin(auth)) {
      return null;
    }
    if (hasAuthority(auth, ROLE_DOCTOR)) {
      return UUID.fromString(auth.getName());
    }
    throw new RuntimeException("Error de permisos: no tienes acceso al historial clínico");
  }

  private boolean hasAuthority(Authentication auth, String authority) {
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(authority::equals);
  }
}
