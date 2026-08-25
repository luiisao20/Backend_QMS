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
import com.devluis.repository.ScheduleRepository;
import com.devluis.repository.ServiceRepository;
import com.devluis.repository.StablishmentRepository;
import com.devluis.repository.TurnRepository;

@ExtendWith(MockitoExtension.class)
class ServicioServiceTest {

  @Mock
  private ServiceRepository serviceRepository;
  @Mock
  private DoctorRepository doctorRepository;
  @Mock
  private ScheduleRepository scheduleRepository;
  @Mock
  private StablishmentRepository stablishmentRepository;
  @Mock
  private TurnRepository turnRepository;

  private ServicioService servicioService;

  @BeforeEach
  void setUp() {
    servicioService = new ServicioService(
        serviceRepository, doctorRepository, scheduleRepository, stablishmentRepository, turnRepository);
  }

  @Test
  void delete_removesService_whenNoneOfItsSchedulesHaveBookedTurns() {
    when(serviceRepository.existsById(1L)).thenReturn(true);
    when(turnRepository.existsByScheduleServiceId(1L)).thenReturn(false);

    servicioService.delete(1L);

    verify(serviceRepository).deleteById(1L);
  }

  @Test
  void delete_throwsClearSpanishMessage_whenAScheduleHasBookedTurns() {
    when(serviceRepository.existsById(1L)).thenReturn(true);
    when(turnRepository.existsByScheduleServiceId(1L)).thenReturn(true);

    assertThatThrownBy(() -> servicioService.delete(1L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("turnos");

    verify(serviceRepository, never()).deleteById(any());
  }

  @Test
  void delete_throws_whenServiceDoesNotExist() {
    when(serviceRepository.existsById(1L)).thenReturn(false);

    assertThatThrownBy(() -> servicioService.delete(1L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Servicio no encontrado");

    verify(turnRepository, never()).existsByScheduleServiceId(any());
    verify(serviceRepository, never()).deleteById(any());
  }
}
