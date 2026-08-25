package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import com.devluis.dto.PromotionDTO;
import com.devluis.dto.ServicioDTO;
import com.devluis.entity.Promotion;
import com.devluis.entity.Servicio;
import com.devluis.repository.PromotionRepository;
import com.devluis.repository.ServiceRepository;
import com.devluis.types.DiscountType;

/**
 * Overlap rule under test (see Promotion's docblock): a create/update is
 * REJECTED outright when its [startDate, endDate] window would overlap
 * another Promotion already on file for the SAME Servicio. Chosen over
 * silently deactivating the older promotion or allowing both to stack,
 * specifically so the database can never hold two simultaneously-active
 * promotions for one service — CoveragePricingService then never needs a
 * tie-break rule.
 */
@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

  @Mock
  private PromotionRepository promotionRepository;
  @Mock
  private ServiceRepository serviceRepository;

  private PromotionService promotionService;

  @BeforeEach
  void setUp() {
    promotionService = new PromotionService(promotionRepository, serviceRepository);
  }

  private Servicio servicio() {
    return Servicio.builder().id(1L).name("Blanqueamiento dental").price(100f).build();
  }

  private PromotionDTO validDto(LocalDate start, LocalDate end) {
    return PromotionDTO.builder()
        .servicio(ServicioDTO.builder().id(1L).build())
        .name("Promo Verano")
        .discountType(DiscountType.PERCENTAGE)
        .discountValue(new BigDecimal("20"))
        .startDate(start)
        .endDate(end)
        .build();
  }

  @Test
  void create_savesAndReturnsThePromotion_whenNoOverlap() {
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));
    when(promotionRepository.existsOverlapping(eq(1L), any(), any(), isNull())).thenReturn(false);
    when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> {
      Promotion p = inv.getArgument(0);
      p.setId(10L);
      return p;
    });

    PromotionDTO result = promotionService.create(validDto(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

    assertThat(result.getId()).isEqualTo(10L);
    assertThat(result.getName()).isEqualTo("Promo Verano");
    assertThat(result.getServicio().getId()).isEqualTo(1L);
  }

  @Test
  void create_throws_whenServicioNotFound() {
    when(serviceRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> promotionService.create(validDto(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Servicio no encontrado");

    verify(promotionRepository, never()).save(any());
  }

  @Test
  void create_throws_whenEndDateIsBeforeStartDate() {
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));

    assertThatThrownBy(() -> promotionService.create(validDto(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("fecha de fin");

    verify(promotionRepository, never()).save(any());
  }

  @Test
  void create_throws_whenPercentageValueExceeds100() {
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));
    PromotionDTO dto = validDto(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    dto.setDiscountValue(new BigDecimal("150"));

    assertThatThrownBy(() -> promotionService.create(dto))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("100");

    verify(promotionRepository, never()).save(any());
  }

  @Test
  void create_allowsFixedAmountValueAbove100_sinceItIsNotAPercentage() {
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));
    when(promotionRepository.existsOverlapping(eq(1L), any(), any(), isNull())).thenReturn(false);
    when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));
    PromotionDTO dto = validDto(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    dto.setDiscountType(DiscountType.FIXED_AMOUNT);
    dto.setDiscountValue(new BigDecimal("500"));

    PromotionDTO result = promotionService.create(dto);

    assertThat(result.getDiscountValue()).isEqualByComparingTo("500");
  }

  @Test
  void create_throws_whenDateRangeOverlapsAnExistingPromotionForTheSameService() {
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));
    when(promotionRepository.existsOverlapping(1L, LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 15), null))
        .thenReturn(true);

    assertThatThrownBy(() -> promotionService.create(
        validDto(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 15))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("vigente");

    verify(promotionRepository, never()).save(any());
  }

  @Test
  void getAll_withServicioFilter_delegatesToFindByServicioId() {
    Pageable pageable = PageRequest.of(0, 10);
    Promotion entity = Promotion.builder().id(1L).servicio(servicio()).name("Promo")
        .discountType(DiscountType.PERCENTAGE).discountValue(new BigDecimal("10"))
        .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 1, 31)).build();
    when(promotionRepository.findByServicioId(eq(1L), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(entity)));

    Page<PromotionDTO> result = promotionService.getAll(1L, pageable);

    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  void getAll_withoutFilter_delegatesToFindAll() {
    Pageable pageable = PageRequest.of(0, 10);
    when(promotionRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

    Page<PromotionDTO> result = promotionService.getAll(pageable);

    assertThat(result.getContent()).isEmpty();
    verify(promotionRepository, never()).findByServicioId(anyLong(), any());
  }

  @Test
  void getById_marksCurrentlyActive_whenTodayIsWithinRange() {
    Promotion entity = Promotion.builder().id(1L).servicio(servicio()).name("Promo")
        .discountType(DiscountType.PERCENTAGE).discountValue(new BigDecimal("10"))
        .startDate(LocalDate.now().minusDays(1)).endDate(LocalDate.now().plusDays(1)).build();
    when(promotionRepository.findById(1L)).thenReturn(Optional.of(entity));

    PromotionDTO result = promotionService.getById(1L);

    assertThat(result.getCurrentlyActive()).isTrue();
  }

  @Test
  void getById_marksNotCurrentlyActive_whenRangeIsInTheFuture() {
    Promotion entity = Promotion.builder().id(1L).servicio(servicio()).name("Promo")
        .discountType(DiscountType.PERCENTAGE).discountValue(new BigDecimal("10"))
        .startDate(LocalDate.now().plusDays(10)).endDate(LocalDate.now().plusDays(20)).build();
    when(promotionRepository.findById(1L)).thenReturn(Optional.of(entity));

    PromotionDTO result = promotionService.getById(1L);

    assertThat(result.getCurrentlyActive()).isFalse();
  }

  @Test
  void getById_throws_whenNotFound() {
    when(promotionRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> promotionService.getById(404L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrada");
  }

  @Test
  void update_changesFields_whenNoOverlapExcludingItself() {
    Promotion existing = Promotion.builder().id(5L).servicio(servicio()).name("Vieja")
        .discountType(DiscountType.PERCENTAGE).discountValue(new BigDecimal("10"))
        .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 1, 31)).build();
    when(promotionRepository.findById(5L)).thenReturn(Optional.of(existing));
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));
    when(promotionRepository.existsOverlapping(1L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), 5L))
        .thenReturn(false);
    when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

    PromotionDTO result = promotionService.update(5L,
        validDto(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)));

    assertThat(result.getStartDate()).isEqualTo(LocalDate.of(2026, 2, 1));
  }

  @Test
  void update_throws_whenNotFound() {
    when(promotionRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> promotionService.update(404L,
        validDto(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrada");
  }

  @Test
  void update_throws_whenOverlapsAnotherExistingPromotion() {
    Promotion existing = Promotion.builder().id(5L).servicio(servicio()).name("Vieja")
        .discountType(DiscountType.PERCENTAGE).discountValue(new BigDecimal("10"))
        .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 1, 31)).build();
    when(promotionRepository.findById(5L)).thenReturn(Optional.of(existing));
    when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicio()));
    when(promotionRepository.existsOverlapping(1L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), 5L))
        .thenReturn(true);

    assertThatThrownBy(() -> promotionService.update(5L,
        validDto(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("vigente");

    verify(promotionRepository, never()).save(any());
  }

  @Test
  void delete_removesPromotion_whenExists() {
    when(promotionRepository.existsById(1L)).thenReturn(true);

    promotionService.delete(1L);

    verify(promotionRepository).deleteById(1L);
  }

  @Test
  void delete_throws_whenNotFound() {
    when(promotionRepository.existsById(404L)).thenReturn(false);

    assertThatThrownBy(() -> promotionService.delete(404L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrada");

    verify(promotionRepository, never()).deleteById(any());
  }
}
