package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.devluis.repository.DoctorRepository;
import com.devluis.repository.ServiceRepository;
import com.devluis.repository.StablishmentRepository;
import com.devluis.repository.TurnRepository;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

  @Mock
  private DoctorRepository doctorRepository;
  @Mock
  private PasswordEncoder passwordEncoder;
  @Mock
  private StablishmentRepository stablishmentRepository;
  @Mock
  private ServiceRepository serviceRepository;
  @Mock
  private TurnRepository turnRepository;

  private DoctorService buildService() {
    return new DoctorService(doctorRepository, passwordEncoder, stablishmentRepository, serviceRepository, turnRepository);
  }

  @Test
  void deleteDoctor_removesDoctor_whenNoneOfItsSchedulesHaveBookedTurns() {
    UUID doctorId = UUID.randomUUID();
    when(doctorRepository.existsById(doctorId)).thenReturn(true);
    when(turnRepository.existsByScheduleDoctorUuid(doctorId)).thenReturn(false);

    buildService().deleteDoctor(doctorId);

    verify(doctorRepository).deleteById(doctorId);
  }

  @Test
  void deleteDoctor_throwsClearSpanishMessage_whenAScheduleHasBookedTurns() {
    UUID doctorId = UUID.randomUUID();
    when(doctorRepository.existsById(doctorId)).thenReturn(true);
    when(turnRepository.existsByScheduleDoctorUuid(doctorId)).thenReturn(true);

    assertThatThrownBy(() -> buildService().deleteDoctor(doctorId))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("turnos");

    verify(doctorRepository, never()).deleteById(any());
  }

  @Test
  void deleteDoctor_throws_whenDoctorDoesNotExist() {
    UUID doctorId = UUID.randomUUID();
    when(doctorRepository.existsById(doctorId)).thenReturn(false);

    assertThatThrownBy(() -> buildService().deleteDoctor(doctorId))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Doctor no encontrado");

    verify(turnRepository, never()).existsByScheduleDoctorUuid(any());
    verify(doctorRepository, never()).deleteById(any());
  }
}
