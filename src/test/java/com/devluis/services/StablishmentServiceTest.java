package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devluis.repository.DoctorRepository;
import com.devluis.repository.OperatorRepository;
import com.devluis.repository.ServiceRepository;
import com.devluis.repository.StablishmentRepository;
import com.devluis.repository.TurnRepository;

@ExtendWith(MockitoExtension.class)
class StablishmentServiceTest {

  @Mock
  private StablishmentRepository stablishmentRepository;
  @Mock
  private ServiceRepository serviceRepository;
  @Mock
  private DoctorRepository doctorRepository;
  @Mock
  private OperatorRepository operatorRepository;
  @Mock
  private TurnRepository turnRepository;

  private StablishmentService stablishmentService;

  @BeforeEach
  void setUp() {
    stablishmentService = new StablishmentService(
        stablishmentRepository, serviceRepository, doctorRepository, operatorRepository, turnRepository);
  }

  @Test
  void delete_removesStablishment_whenNoneOfItsSchedulesHaveBookedTurns() {
    when(stablishmentRepository.existsById(1L)).thenReturn(true);
    when(turnRepository.existsByScheduleStablishmentId(1L)).thenReturn(false);

    stablishmentService.delete(1L);

    verify(stablishmentRepository).deleteById(1L);
  }

  @Test
  void delete_throwsClearSpanishMessage_whenAScheduleHasBookedTurns() {
    when(stablishmentRepository.existsById(1L)).thenReturn(true);
    when(turnRepository.existsByScheduleStablishmentId(1L)).thenReturn(true);

    assertThatThrownBy(() -> stablishmentService.delete(1L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("turnos");

    verify(stablishmentRepository, never()).deleteById(any());
  }

  @Test
  void delete_throws_whenStablishmentDoesNotExist() {
    when(stablishmentRepository.existsById(1L)).thenReturn(false);

    assertThatThrownBy(() -> stablishmentService.delete(1L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Establecimiento no encontrado");

    verify(turnRepository, never()).existsByScheduleStablishmentId(any());
    verify(stablishmentRepository, never()).deleteById(any());
  }
}
