package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Same shape and reasoning as ClinicalAccessGuardTest: a plain injectable
 * POJO invoked imperatively from PatientCoverageService, tested with plain
 * JUnit because this codebase's {@code @WebMvcTest} slices run with
 * {@code addFilters = false} and never load {@code @EnableMethodSecurity}.
 */
class PatientCoverageAccessGuardTest {

  private final PatientCoverageAccessGuard guard = new PatientCoverageAccessGuard();

  private Authentication authOf(UUID uuid, String... authorities) {
    List<SimpleGrantedAuthority> granted = List.of(authorities).stream()
        .map(SimpleGrantedAuthority::new)
        .toList();
    return new UsernamePasswordAuthenticationToken(uuid.toString(), null, granted);
  }

  @Test
  void isStaff_true_forEmployee() {
    assertThat(guard.isStaff(authOf(UUID.randomUUID(), "ROLE_EMPLOYEE"))).isTrue();
  }

  @Test
  void isStaff_true_forAdmin() {
    assertThat(guard.isStaff(authOf(UUID.randomUUID(), "ROLE_ADMIN"))).isTrue();
  }

  @Test
  void isStaff_false_forDoctor_becauseBillingIsNotClinicalNeedToKnow() {
    assertThat(guard.isStaff(authOf(UUID.randomUUID(), "ROLE_DOCTOR"))).isFalse();
  }

  @Test
  void isStaff_false_forPatient() {
    assertThat(guard.isStaff(authOf(UUID.randomUUID(), "ROLE_PATIENT"))).isFalse();
  }

  @Test
  void isOwner_true_whenPatientRoleAndUuidMatches() {
    UUID patientUuid = UUID.randomUUID();
    assertThat(guard.isOwner(authOf(patientUuid, "ROLE_PATIENT"), patientUuid)).isTrue();
  }

  @Test
  void isOwner_false_whenUuidDoesNotMatch() {
    UUID patientUuid = UUID.randomUUID();
    Authentication otherPatient = authOf(UUID.randomUUID(), "ROLE_PATIENT");
    assertThat(guard.isOwner(otherPatient, patientUuid)).isFalse();
  }

  @Test
  void isOwner_false_whenRoleIsNotPatient_evenIfUuidMatches() {
    UUID uuid = UUID.randomUUID();
    // An admin sharing the same UUID string coincidence must never be
    // treated as "the owning patient" — the role check is not optional.
    assertThat(guard.isOwner(authOf(uuid, "ROLE_ADMIN"), uuid)).isFalse();
  }

  @Test
  void isOwner_false_whenPatientUuidIsNull() {
    assertThat(guard.isOwner(authOf(UUID.randomUUID(), "ROLE_PATIENT"), null)).isFalse();
  }

  @Test
  void assertCanAccessCoverage_passes_forEmployee_regardlessOfOwner() {
    guard.assertCanAccessCoverage(authOf(UUID.randomUUID(), "ROLE_EMPLOYEE"), UUID.randomUUID());
    // no exception => pass
  }

  @Test
  void assertCanAccessCoverage_passes_forAdmin_regardlessOfOwner() {
    guard.assertCanAccessCoverage(authOf(UUID.randomUUID(), "ROLE_ADMIN"), UUID.randomUUID());
  }

  @Test
  void assertCanAccessCoverage_passes_forTheOwningPatient() {
    UUID patientUuid = UUID.randomUUID();
    guard.assertCanAccessCoverage(authOf(patientUuid, "ROLE_PATIENT"), patientUuid);
  }

  @Test
  void assertCanAccessCoverage_throws_forADifferentPatient() {
    UUID patientUuid = UUID.randomUUID();
    Authentication otherPatient = authOf(UUID.randomUUID(), "ROLE_PATIENT");

    assertThatThrownBy(() -> guard.assertCanAccessCoverage(otherPatient, patientUuid))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("permisos");
  }

  @Test
  void assertCanAccessCoverage_throws_forDoctor_evenWhenNotTargetingAnyoneSpecific() {
    UUID patientUuid = UUID.randomUUID();
    Authentication doctor = authOf(UUID.randomUUID(), "ROLE_DOCTOR");

    assertThatThrownBy(() -> guard.assertCanAccessCoverage(doctor, patientUuid))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("permisos");
  }
}
