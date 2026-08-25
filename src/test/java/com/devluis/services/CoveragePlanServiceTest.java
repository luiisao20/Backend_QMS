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

import com.devluis.dto.CoveragePlanDTO;
import com.devluis.dto.InsurerDTO;
import com.devluis.entity.CoveragePlan;
import com.devluis.entity.Insurer;
import com.devluis.repository.CoveragePlanRepository;
import com.devluis.repository.InsurerRepository;
import com.devluis.repository.PatientCoverageRepository;
import com.devluis.types.InsurerType;

@ExtendWith(MockitoExtension.class)
class CoveragePlanServiceTest {

  @Mock
  private CoveragePlanRepository coveragePlanRepository;
  @Mock
  private InsurerRepository insurerRepository;
  @Mock
  private PatientCoverageRepository patientCoverageRepository;

  private CoveragePlanService coveragePlanService;

  @BeforeEach
  void setUp() {
    coveragePlanService =
        new CoveragePlanService(coveragePlanRepository, insurerRepository, patientCoverageRepository);
  }

  private Insurer insurer() {
    return Insurer.builder().id(1L).name("Seguros Sucre").type(InsurerType.INSURER_PRIVATE).build();
  }

  private CoveragePlanDTO validDto() {
    return CoveragePlanDTO.builder()
        .insurer(InsurerDTO.builder().id(1L).build())
        .name("Plan Oro")
        .coveragePercentage(80)
        .copayAmount(null)
        .build();
  }

  @Test
  void create_resolvesInsurer_andSavesThePlan() {
    when(insurerRepository.findById(1L)).thenReturn(Optional.of(insurer()));
    when(coveragePlanRepository.save(any(CoveragePlan.class))).thenAnswer(inv -> {
      CoveragePlan p = inv.getArgument(0);
      p.setId(10L);
      return p;
    });

    CoveragePlanDTO result = coveragePlanService.create(validDto());

    assertThat(result.getId()).isEqualTo(10L);
    assertThat(result.getInsurer().getName()).isEqualTo("Seguros Sucre");
    assertThat(result.getCoveragePercentage()).isEqualTo(80);
  }

  @Test
  void create_throws_whenInsurerNotFound() {
    when(insurerRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> coveragePlanService.create(validDto()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Aseguradora no encontrada");

    verify(coveragePlanRepository, never()).save(any());
  }

  @Test
  void getAll_withInsurerFilter_delegatesToFindByInsurerId() {
    Pageable pageable = PageRequest.of(0, 10);
    CoveragePlan plan = CoveragePlan.builder().id(1L).insurer(insurer()).name("Plan Oro")
        .coveragePercentage(80).build();
    when(coveragePlanRepository.findByInsurerId(eq(1L), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(plan)));

    Page<CoveragePlanDTO> result = coveragePlanService.getAll(1L, pageable);

    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  void getById_returnsMappedDTO_withNestedInsurer() {
    CoveragePlan plan = CoveragePlan.builder().id(5L).insurer(insurer()).name("Plan Plata")
        .coveragePercentage(60).copayAmount(new BigDecimal("5.00")).build();
    when(coveragePlanRepository.findById(5L)).thenReturn(Optional.of(plan));

    CoveragePlanDTO result = coveragePlanService.getById(5L);

    assertThat(result.getName()).isEqualTo("Plan Plata");
    assertThat(result.getInsurer().getId()).isEqualTo(1L);
    assertThat(result.getCopayAmount()).isEqualByComparingTo("5.00");
  }

  @Test
  void getById_throws_whenNotFound() {
    when(coveragePlanRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> coveragePlanService.getById(404L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrado");
  }

  @Test
  void update_changesFields() {
    CoveragePlan existing = CoveragePlan.builder().id(5L).insurer(insurer()).name("Vieja")
        .coveragePercentage(50).build();
    when(coveragePlanRepository.findById(5L)).thenReturn(Optional.of(existing));
    when(insurerRepository.findById(1L)).thenReturn(Optional.of(insurer()));
    when(coveragePlanRepository.save(any(CoveragePlan.class))).thenAnswer(inv -> inv.getArgument(0));

    CoveragePlanDTO result = coveragePlanService.update(5L,
        CoveragePlanDTO.builder().insurer(InsurerDTO.builder().id(1L).build())
            .name("Nueva").coveragePercentage(90).build());

    assertThat(result.getName()).isEqualTo("Nueva");
    assertThat(result.getCoveragePercentage()).isEqualTo(90);
  }

  @Test
  void delete_removesPlan_whenNoPatientCoverageReferencesIt() {
    when(coveragePlanRepository.existsById(5L)).thenReturn(true);
    when(patientCoverageRepository.existsByPlanId(5L)).thenReturn(false);

    coveragePlanService.delete(5L);

    verify(coveragePlanRepository).deleteById(5L);
  }

  @Test
  void delete_throws_whenNotFound() {
    when(coveragePlanRepository.existsById(404L)).thenReturn(false);

    assertThatThrownBy(() -> coveragePlanService.delete(404L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrado");
  }

  @Test
  void delete_throws_whenPatientCoveragesStillReferenceIt() {
    when(coveragePlanRepository.existsById(5L)).thenReturn(true);
    when(patientCoverageRepository.existsByPlanId(5L)).thenReturn(true);

    assertThatThrownBy(() -> coveragePlanService.delete(5L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("coberturas");

    verify(coveragePlanRepository, never()).deleteById(any());
  }
}
