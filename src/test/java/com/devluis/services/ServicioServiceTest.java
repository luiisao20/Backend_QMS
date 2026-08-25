package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devluis.dto.ServicioDTO;
import com.devluis.entity.Servicio;
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

  // --- "precios/descuentos": a purpose-built view/edit over the existing
  // Servicio.discount column (see apply report for why no new entity was
  // created) --------------------------------------------------------------

  @Test
  void getById_includesComputedNetPrice_priceMinusDiscount() {
    Servicio entity = Servicio.builder().id(1L).name("Limpieza").price(100f).discount(15f).build();
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(entity));

    ServicioDTO result = servicioService.getById(1L);

    assertThat(result.getNetPrice()).isEqualByComparingTo("85.00");
  }

  @Test
  void updateDiscount_changesOnlyTheDiscount_leavingNameAndPriceUntouched() {
    Servicio existing = Servicio.builder().id(1L).name("Limpieza").price(100f).discount(0f).build();
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(serviceRepository.save(any(Servicio.class))).thenAnswer(inv -> inv.getArgument(0));

    ServicioDTO result = servicioService.updateDiscount(1L, 20f);

    assertThat(result.getName()).isEqualTo("Limpieza");
    assertThat(result.getPrice()).isEqualTo(100f);
    assertThat(result.getDiscount()).isEqualTo(20f);
    assertThat(result.getNetPrice()).isEqualByComparingTo("80.00");
  }

  @Test
  void updateDiscount_throws_whenServiceNotFound() {
    when(serviceRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servicioService.updateDiscount(404L, 10f))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Servicio no encontrado");

    verify(serviceRepository, never()).save(any());
  }

  @Test
  void updateDiscount_throws_whenDiscountIsNegative() {
    Servicio existing = Servicio.builder().id(1L).name("Limpieza").price(100f).discount(0f).build();
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> servicioService.updateDiscount(1L, -5f))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("negativo");

    verify(serviceRepository, never()).save(any());
  }
}
