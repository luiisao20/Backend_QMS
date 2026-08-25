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
 * The one place that decides "may THIS caller read/write THIS specific
 * clinical record". Deliberately a plain injectable POJO (not SpEL wired into
 * a {@code @PreAuthorize} bean reference) so the rule is testable with plain
 * JUnit — {@code @WebMvcTest} in this codebase never loads
 * {@code @EnableMethodSecurity} (see TurnControllerTest/PatientControllerTest
 * docblocks), so a purely declarative {@code @PreAuthorize} rule here would be
 * untestable end to end, exactly the gap this class avoids.
 */
class ClinicalAccessGuardTest {

  private final ClinicalAccessGuard guard = new ClinicalAccessGuard();

  private Authentication authOf(UUID uuid, String... authorities) {
    List<SimpleGrantedAuthority> granted = List.of(authorities).stream()
        .map(SimpleGrantedAuthority::new)
        .toList();
    return new UsernamePasswordAuthenticationToken(uuid.toString(), null, granted);
  }

  @Test
  void isAdmin_true_forRoleAdmin() {
    assertThat(guard.isAdmin(authOf(UUID.randomUUID(), "ROLE_ADMIN"))).isTrue();
  }

  @Test
  void isAdmin_false_forOtherRoles() {
    assertThat(guard.isAdmin(authOf(UUID.randomUUID(), "ROLE_DOCTOR"))).isFalse();
    assertThat(guard.isAdmin(authOf(UUID.randomUUID(), "ROLE_EMPLOYEE"))).isFalse();
    assertThat(guard.isAdmin(authOf(UUID.randomUUID(), "ROLE_PATIENT"))).isFalse();
  }

  @Test
  void isTreatingDoctor_true_whenDoctorRoleAndUuidMatches() {
    UUID doctorUuid = UUID.randomUUID();
    assertThat(guard.isTreatingDoctor(authOf(doctorUuid, "ROLE_DOCTOR"), doctorUuid)).isTrue();
  }

  @Test
  void isTreatingDoctor_false_whenUuidDoesNotMatch() {
    UUID doctorUuid = UUID.randomUUID();
    Authentication otherDoctor = authOf(UUID.randomUUID(), "ROLE_DOCTOR");
    assertThat(guard.isTreatingDoctor(otherDoctor, doctorUuid)).isFalse();
  }

  @Test
  void isTreatingDoctor_false_whenRoleIsNotDoctor_evenIfUuidMatches() {
    UUID uuid = UUID.randomUUID();
    // An admin sharing the same UUID string coincidence must never be treated
    // as "the treating doctor" — the role check is not optional.
    assertThat(guard.isTreatingDoctor(authOf(uuid, "ROLE_ADMIN"), uuid)).isFalse();
  }

  @Test
  void isTreatingDoctor_false_whenTreatingDoctorUuidIsNull() {
    assertThat(guard.isTreatingDoctor(authOf(UUID.randomUUID(), "ROLE_DOCTOR"), null)).isFalse();
  }

  @Test
  void assertCanAccessEncounter_passes_forAdmin_regardlessOfTreatingDoctor() {
    UUID treatingDoctorUuid = UUID.randomUUID();
    guard.assertCanAccessEncounter(authOf(UUID.randomUUID(), "ROLE_ADMIN"), treatingDoctorUuid);
    // no exception => pass
  }

  @Test
  void assertCanAccessEncounter_passes_forTheTreatingDoctor() {
    UUID treatingDoctorUuid = UUID.randomUUID();
    guard.assertCanAccessEncounter(authOf(treatingDoctorUuid, "ROLE_DOCTOR"), treatingDoctorUuid);
  }

  @Test
  void assertCanAccessEncounter_throws_forADifferentDoctor() {
    UUID treatingDoctorUuid = UUID.randomUUID();
    Authentication otherDoctor = authOf(UUID.randomUUID(), "ROLE_DOCTOR");

    assertThatThrownBy(() -> guard.assertCanAccessEncounter(otherDoctor, treatingDoctorUuid))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("permisos");
  }

  @Test
  void assertCanAccessEncounter_throws_forEmployee() {
    UUID treatingDoctorUuid = UUID.randomUUID();
    Authentication employee = authOf(UUID.randomUUID(), "ROLE_EMPLOYEE");

    assertThatThrownBy(() -> guard.assertCanAccessEncounter(employee, treatingDoctorUuid))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("permisos");
  }

  @Test
  void resolveDoctorFilter_returnsNull_forAdmin_meaningNoFilter() {
    assertThat(guard.resolveDoctorFilter(authOf(UUID.randomUUID(), "ROLE_ADMIN"))).isNull();
  }

  @Test
  void resolveDoctorFilter_returnsOwnUuid_forDoctor() {
    UUID doctorUuid = UUID.randomUUID();
    assertThat(guard.resolveDoctorFilter(authOf(doctorUuid, "ROLE_DOCTOR"))).isEqualTo(doctorUuid);
  }

  @Test
  void resolveDoctorFilter_throws_forEmployeeOrPatient() {
    assertThatThrownBy(() -> guard.resolveDoctorFilter(authOf(UUID.randomUUID(), "ROLE_EMPLOYEE")))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> guard.resolveDoctorFilter(authOf(UUID.randomUUID(), "ROLE_PATIENT")))
        .isInstanceOf(RuntimeException.class);
  }
}
