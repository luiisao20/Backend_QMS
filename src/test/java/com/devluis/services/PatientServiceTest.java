package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.devluis.dto.PatientDTO;
import com.devluis.entity.Patient;
import com.devluis.repository.PatientRepository;

/**
 * Regression coverage for {@link PatientService#getPatientById}, which
 * existed and worked before this change but had no caller (and no test)
 * until {@code PatientController} wired it up to GET /api/patients/{id} and
 * GET /api/patients/me. Not a RED-then-GREEN cycle: the method was already
 * correct, this just locks its contract in before it gets a real caller.
 */
@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

  @Mock
  private PasswordEncoder passwordEncoder;
  @Mock
  private PatientRepository patientRepository;

  private PatientService patientService;

  @BeforeEach
  void setUp() {
    patientService = new PatientService(passwordEncoder, patientRepository);
  }

  @Test
  void getPatientById_returnsMappedDto_whenPatientExists() {
    UUID id = UUID.randomUUID();
    Patient patient = Patient.builder()
        .uuid(id)
        .email("a@b.com")
        .firstName("Ana")
        .lastName("Lopez")
        .ci("1234567890")
        .birthday(LocalDate.of(1990, 2, 2))
        .build();
    when(patientRepository.findById(id)).thenReturn(Optional.of(patient));

    PatientDTO dto = patientService.getPatientById(id);

    assertThat(dto.getUuid()).isEqualTo(id);
    assertThat(dto.getEmail()).isEqualTo("a@b.com");
    assertThat(dto.getFirstName()).isEqualTo("Ana");
    assertThat(dto.getLastName()).isEqualTo("Lopez");
    assertThat(dto.getCi()).isEqualTo("1234567890");
  }

  @Test
  void getPatientById_throwsSpanishMessage_whenPatientDoesNotExist() {
    UUID id = UUID.randomUUID();
    when(patientRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> patientService.getPatientById(id))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Paciente no encontrado");
  }
}
