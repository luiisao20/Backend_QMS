package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.devluis.dto.ServicioDTO;
import com.devluis.dto.SessionPlanDTO;
import com.devluis.entity.SessionPlan;
import com.devluis.entity.Servicio;
import com.devluis.repository.ServiceRepository;
import com.devluis.repository.SessionPlanRepository;

@ExtendWith(MockitoExtension.class)
class SessionPlanServiceTest {

  @Mock
  private SessionPlanRepository sessionPlanRepository;
  @Mock
  private ServiceRepository serviceRepository;

  private SessionPlanService sessionPlanService;

  @BeforeEach
  void setUp() {
    sessionPlanService = new SessionPlanService(sessionPlanRepository, serviceRepository);
  }

  private Servicio fisioterapia() {
    return Servicio.builder().id(1L).name("Fisioterapia").price(20f).discount(null).build();
  }

  private SessionPlanDTO validDto() {
    return SessionPlanDTO.builder()
        .servicio(ServicioDTO.builder().id(1L).build())
        .name("Plan 10 sesiones")
        .sessionCount(10)
        .price(new BigDecimal("180.00"))
        .build();
  }

  @Test
  void create_savesAndReturnsThePlan_withComputedTotals() {
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(fisioterapia()));
    when(sessionPlanRepository.save(any(SessionPlan.class))).thenAnswer(inv -> {
      SessionPlan p = inv.getArgument(0);
      p.setId(5L);
      return p;
    });

    SessionPlanDTO result = sessionPlanService.create(validDto());

    assertThat(result.getId()).isEqualTo(5L);
    // pricePerSession = 180.00 / 10 = 18.00
    assertThat(result.getPricePerSession()).isEqualByComparingTo("18.00");
    // regularTotal = 10 * 20.00 (netPrice, no discount) = 200.00
    assertThat(result.getRegularTotal()).isEqualByComparingTo("200.00");
    // savings = 200.00 - 180.00 = 20.00
    assertThat(result.getSavings()).isEqualByComparingTo("20.00");
  }

  @Test
  void create_pricePerSession_roundsHalfUp_onARepeatingDecimal() {
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(fisioterapia()));
    when(sessionPlanRepository.save(any(SessionPlan.class))).thenAnswer(inv -> inv.getArgument(0));
    SessionPlanDTO dto = validDto();
    dto.setSessionCount(3);
    dto.setPrice(new BigDecimal("10.00"));

    SessionPlanDTO result = sessionPlanService.create(dto);

    // 10.00 / 3 = 3.3333... -> HALF_UP to 3.33
    assertThat(result.getPricePerSession()).isEqualByComparingTo("3.33");
  }

  @Test
  void create_allowsNegativeSavings_whenPriceExceedsTheRegularTotal() {
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(fisioterapia()));
    when(sessionPlanRepository.save(any(SessionPlan.class))).thenAnswer(inv -> inv.getArgument(0));
    SessionPlanDTO dto = validDto();
    dto.setPrice(new BigDecimal("999.00"));

    SessionPlanDTO result = sessionPlanService.create(dto);

    assertThat(result.getSavings()).isEqualByComparingTo("-799.00");
  }

  @Test
  void create_throws_whenServicioNotFound() {
    when(serviceRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sessionPlanService.create(validDto()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Servicio no encontrado");

    verify(sessionPlanRepository, never()).save(any());
  }

  @Test
  void getAll_withServicioFilter_delegatesToFindByServicioId() {
    Pageable pageable = PageRequest.of(0, 10);
    SessionPlan entity = SessionPlan.builder().id(1L).servicio(fisioterapia()).name("Plan")
        .sessionCount(10).price(new BigDecimal("180.00")).build();
    when(sessionPlanRepository.findByServicioId(eq(1L), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(entity)));

    Page<SessionPlanDTO> result = sessionPlanService.getAll(1L, pageable);

    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  void getAll_withoutFilter_delegatesToFindAll() {
    Pageable pageable = PageRequest.of(0, 10);
    when(sessionPlanRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

    Page<SessionPlanDTO> result = sessionPlanService.getAll(pageable);

    assertThat(result.getContent()).isEmpty();
  }

  @Test
  void getById_returnsMappedDTO_whenFound() {
    SessionPlan entity = SessionPlan.builder().id(1L).servicio(fisioterapia()).name("Plan")
        .sessionCount(10).price(new BigDecimal("180.00")).build();
    when(sessionPlanRepository.findById(1L)).thenReturn(Optional.of(entity));

    SessionPlanDTO result = sessionPlanService.getById(1L);

    assertThat(result.getName()).isEqualTo("Plan");
  }

  @Test
  void getById_throws_whenNotFound() {
    when(sessionPlanRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sessionPlanService.getById(404L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrado");
  }

  @Test
  void update_changesFields() {
    SessionPlan existing = SessionPlan.builder().id(1L).servicio(fisioterapia()).name("Vieja")
        .sessionCount(5).price(new BigDecimal("90.00")).build();
    when(sessionPlanRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(fisioterapia()));
    when(sessionPlanRepository.save(any(SessionPlan.class))).thenAnswer(inv -> inv.getArgument(0));

    SessionPlanDTO result = sessionPlanService.update(1L, validDto());

    assertThat(result.getName()).isEqualTo("Plan 10 sesiones");
    assertThat(result.getSessionCount()).isEqualTo(10);
  }

  @Test
  void update_throws_whenNotFound() {
    when(sessionPlanRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sessionPlanService.update(404L, validDto()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrado");
  }

  @Test
  void delete_removesPlan_whenExists() {
    when(sessionPlanRepository.existsById(1L)).thenReturn(true);

    sessionPlanService.delete(1L);

    verify(sessionPlanRepository).deleteById(1L);
  }

  @Test
  void delete_throws_whenNotFound() {
    when(sessionPlanRepository.existsById(404L)).thenReturn(false);

    assertThatThrownBy(() -> sessionPlanService.delete(404L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrado");

    verify(sessionPlanRepository, never()).deleteById(any());
  }
}
