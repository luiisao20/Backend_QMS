package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.devluis.dto.CoverageQuoteDTO;
import com.devluis.dto.CoveragePlanDTO;
import com.devluis.dto.PatientCoverageDTO;
import com.devluis.dto.PatientDTO;
import com.devluis.entity.CoveragePlan;
import com.devluis.entity.Insurer;
import com.devluis.entity.Patient;
import com.devluis.entity.PatientCoverage;
import com.devluis.entity.Promotion;
import com.devluis.entity.Servicio;
import com.devluis.repository.CoveragePlanRepository;
import com.devluis.repository.PatientCoverageRepository;
import com.devluis.repository.PatientRepository;
import com.devluis.repository.PromotionRepository;
import com.devluis.repository.ServiceRepository;
import com.devluis.types.InsurerType;

@ExtendWith(MockitoExtension.class)
class PatientCoverageServiceTest {

  @Mock
  private PatientCoverageRepository patientCoverageRepository;
  @Mock
  private PatientRepository patientRepository;
  @Mock
  private CoveragePlanRepository coveragePlanRepository;
  @Mock
  private ServiceRepository serviceRepository;
  @Mock
  private PromotionRepository promotionRepository;
  @Mock
  private PatientCoverageAccessGuard patientCoverageAccessGuard;
  @Mock
  private CoveragePricingService coveragePricingService;

  private PatientCoverageService patientCoverageService;

  private final UUID patientUuid = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    patientCoverageService = new PatientCoverageService(
        patientCoverageRepository, patientRepository, coveragePlanRepository, serviceRepository,
        promotionRepository, patientCoverageAccessGuard, coveragePricingService);
  }

  private Patient patient() {
    return Patient.builder().uuid(patientUuid).firstName("Ana").lastName("Lopez").build();
  }

  private CoveragePlan plan() {
    Insurer insurer = Insurer.builder().id(1L).name("Seguros Sucre").type(InsurerType.INSURER_PRIVATE).build();
    return CoveragePlan.builder().id(1L).insurer(insurer).name("Plan Oro").coveragePercentage(80).build();
  }

  private PatientCoverageDTO validDto(LocalDate from, LocalDate until, boolean active) {
    return PatientCoverageDTO.builder()
        .patient(PatientDTO.builder().uuid(patientUuid).build())
        .plan(CoveragePlanDTO.builder().id(1L).build())
        .policyNumber("POL-123")
        .validFrom(from)
        .validUntil(until)
        .active(active)
        .build();
  }

  @Test
  void create_savesCoverage_whenPatientAndPlanExist() {
    when(patientRepository.findById(patientUuid)).thenReturn(Optional.of(patient()));
    when(coveragePlanRepository.findById(1L)).thenReturn(Optional.of(plan()));
    when(patientCoverageRepository.save(any(PatientCoverage.class))).thenAnswer(inv -> {
      PatientCoverage c = inv.getArgument(0);
      c.setId(100L);
      return c;
    });
    when(patientCoverageRepository.findByPatientUuidAndActiveTrueAndIdNot(patientUuid, 100L))
        .thenReturn(List.of());

    PatientCoverageDTO result = patientCoverageService.create(
        validDto(LocalDate.of(2026, 1, 1), null, true));

    assertThat(result.getId()).isEqualTo(100L);
    assertThat(result.getPolicyNumber()).isEqualTo("POL-123");
    assertThat(result.getPlan().getName()).isEqualTo("Plan Oro");
  }

  @Test
  void create_throws_whenPatientNotFound() {
    when(patientRepository.findById(patientUuid)).thenReturn(Optional.empty());

    PatientCoverageDTO dto = validDto(LocalDate.of(2026, 1, 1), null, true);

    assertThatThrownBy(() -> patientCoverageService.create(dto))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Paciente no encontrado");

    verify(patientCoverageRepository, never()).save(any());
  }

  @Test
  void create_throws_whenPlanNotFound() {
    when(patientRepository.findById(patientUuid)).thenReturn(Optional.of(patient()));
    when(coveragePlanRepository.findById(1L)).thenReturn(Optional.empty());

    PatientCoverageDTO dto = validDto(LocalDate.of(2026, 1, 1), null, true);

    assertThatThrownBy(() -> patientCoverageService.create(dto))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Plan de cobertura no encontrado");

    verify(patientCoverageRepository, never()).save(any());
  }

  @Test
  void create_throws_whenValidUntilIsBeforeValidFrom() {
    when(patientRepository.findById(patientUuid)).thenReturn(Optional.of(patient()));
    when(coveragePlanRepository.findById(1L)).thenReturn(Optional.of(plan()));

    PatientCoverageDTO dto = validDto(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1), true);

    assertThatThrownBy(() -> patientCoverageService.create(dto))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("vigencia");

    verify(patientCoverageRepository, never()).save(any());
  }

  @Test
  void create_withNullValidUntil_isAllowed_meansOngoingCoverage() {
    when(patientRepository.findById(patientUuid)).thenReturn(Optional.of(patient()));
    when(coveragePlanRepository.findById(1L)).thenReturn(Optional.of(plan()));
    when(patientCoverageRepository.save(any(PatientCoverage.class))).thenAnswer(inv -> inv.getArgument(0));
    when(patientCoverageRepository.findByPatientUuidAndActiveTrueAndIdNot(eq(patientUuid), any()))
        .thenReturn(List.of());

    PatientCoverageDTO result = patientCoverageService.create(
        validDto(LocalDate.of(2026, 1, 1), null, true));

    assertThat(result.getValidUntil()).isNull();
  }

  @Test
  void create_active_deactivatesAnyOtherActiveCoverageForTheSamePatient() {
    when(patientRepository.findById(patientUuid)).thenReturn(Optional.of(patient()));
    when(coveragePlanRepository.findById(1L)).thenReturn(Optional.of(plan()));
    when(patientCoverageRepository.save(any(PatientCoverage.class))).thenAnswer(inv -> {
      PatientCoverage c = inv.getArgument(0);
      c.setId(200L);
      return c;
    });
    PatientCoverage previouslyActive = PatientCoverage.builder().id(150L).patient(patient()).active(true).build();
    when(patientCoverageRepository.findByPatientUuidAndActiveTrueAndIdNot(patientUuid, 200L))
        .thenReturn(List.of(previouslyActive));

    patientCoverageService.create(validDto(LocalDate.of(2026, 1, 1), null, true));

    assertThat(previouslyActive.isActive()).isFalse();
    verify(patientCoverageRepository).saveAll(List.of(previouslyActive));
  }

  @Test
  void create_inactive_doesNotTouchOtherCoverages() {
    when(patientRepository.findById(patientUuid)).thenReturn(Optional.of(patient()));
    when(coveragePlanRepository.findById(1L)).thenReturn(Optional.of(plan()));
    when(patientCoverageRepository.save(any(PatientCoverage.class))).thenAnswer(inv -> {
      PatientCoverage c = inv.getArgument(0);
      c.setId(300L);
      return c;
    });

    patientCoverageService.create(validDto(LocalDate.of(2026, 1, 1), null, false));

    verify(patientCoverageRepository, never()).findByPatientUuidAndActiveTrueAndIdNot(any(), any());
    verify(patientCoverageRepository, never()).saveAll(anyList());
  }

  @Test
  void listForPatient_returnsMappedHistory_orderedAsRepositoryProvides() {
    PatientCoverage c1 = PatientCoverage.builder().id(1L).patient(patient()).plan(plan())
        .policyNumber("A").validFrom(LocalDate.of(2025, 1, 1)).active(false).build();
    PatientCoverage c2 = PatientCoverage.builder().id(2L).patient(patient()).plan(plan())
        .policyNumber("B").validFrom(LocalDate.of(2026, 1, 1)).active(true).build();
    when(patientCoverageRepository.findByPatientUuidOrderByValidFromDesc(patientUuid))
        .thenReturn(List.of(c2, c1));

    List<PatientCoverageDTO> result = patientCoverageService.listForPatient(patientUuid);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getPolicyNumber()).isEqualTo("B");
    assertThat(result.get(0).getActive()).isTrue();
  }

  @Test
  void getById_returnsDTO_afterGuardAllows() {
    PatientCoverage coverage = PatientCoverage.builder().id(9L).patient(patient()).plan(plan())
        .policyNumber("POL-9").validFrom(LocalDate.of(2026, 1, 1)).active(true).build();
    when(patientCoverageRepository.findById(9L)).thenReturn(Optional.of(coverage));
    Authentication auth = staffAuth();

    PatientCoverageDTO result = patientCoverageService.getById(9L, auth);

    assertThat(result.getPolicyNumber()).isEqualTo("POL-9");
    verify(patientCoverageAccessGuard).assertCanAccessCoverage(auth, patientUuid);
  }

  @Test
  void getById_propagatesGuardDenial_forADifferentPatient() {
    PatientCoverage coverage = PatientCoverage.builder().id(9L).patient(patient()).plan(plan())
        .policyNumber("POL-9").validFrom(LocalDate.of(2026, 1, 1)).active(true).build();
    when(patientCoverageRepository.findById(9L)).thenReturn(Optional.of(coverage));
    Authentication otherPatientAuth = new UsernamePasswordAuthenticationToken(
        UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));
    org.mockito.Mockito.doThrow(new RuntimeException("Error de permisos: no tienes acceso a esta cobertura"))
        .when(patientCoverageAccessGuard).assertCanAccessCoverage(otherPatientAuth, patientUuid);

    assertThatThrownBy(() -> patientCoverageService.getById(9L, otherPatientAuth))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("permisos");
  }

  @Test
  void getById_throws_whenNotFound() {
    when(patientCoverageRepository.findById(404L)).thenReturn(Optional.empty());
    Authentication auth = staffAuth();

    assertThatThrownBy(() -> patientCoverageService.getById(404L, auth))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrada");
  }

  @Test
  void update_active_deactivatesOtherActiveCoverages_excludingItself() {
    PatientCoverage existing = PatientCoverage.builder().id(5L).patient(patient()).plan(plan())
        .policyNumber("OLD").validFrom(LocalDate.of(2025, 1, 1)).active(false).build();
    when(patientCoverageRepository.findById(5L)).thenReturn(Optional.of(existing));
    when(patientRepository.findById(patientUuid)).thenReturn(Optional.of(patient()));
    when(coveragePlanRepository.findById(1L)).thenReturn(Optional.of(plan()));
    when(patientCoverageRepository.save(any(PatientCoverage.class))).thenAnswer(inv -> inv.getArgument(0));
    PatientCoverage otherActive = PatientCoverage.builder().id(6L).patient(patient()).active(true).build();
    when(patientCoverageRepository.findByPatientUuidAndActiveTrueAndIdNot(patientUuid, 5L))
        .thenReturn(List.of(otherActive));

    PatientCoverageDTO result = patientCoverageService.update(5L,
        validDto(LocalDate.of(2026, 1, 1), null, true));

    assertThat(result.getActive()).isTrue();
    assertThat(otherActive.isActive()).isFalse();
  }

  @Test
  void update_throws_whenCoverageNotFound() {
    when(patientCoverageRepository.findById(404L)).thenReturn(Optional.empty());

    PatientCoverageDTO dto = validDto(LocalDate.of(2026, 1, 1), null, true);

    assertThatThrownBy(() -> patientCoverageService.update(404L, dto))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrada");
  }

  @Test
  void delete_removesCoverage_whenExists() {
    when(patientCoverageRepository.existsById(5L)).thenReturn(true);

    patientCoverageService.delete(5L);

    verify(patientCoverageRepository).deleteById(5L);
  }

  @Test
  void delete_throws_whenNotFound() {
    when(patientCoverageRepository.existsById(404L)).thenReturn(false);

    assertThatThrownBy(() -> patientCoverageService.delete(404L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("no encontrada");

    verify(patientCoverageRepository, never()).deleteById(any());
  }

  @Test
  void quoteForPatient_delegatesToPricingService_withTheActiveCoverage() {
    Servicio servicio = Servicio.builder().id(7L).name("Consulta").price(50f).build();
    PatientCoverage active = PatientCoverage.builder().id(1L).plan(plan()).active(true).build();
    CoverageQuoteDTO expectedQuote = CoverageQuoteDTO.builder().servicioId(7L).build();
    when(serviceRepository.findById(7L)).thenReturn(Optional.of(servicio));
    when(patientCoverageRepository.findFirstByPatientUuidAndActiveTrue(patientUuid))
        .thenReturn(Optional.of(active));
    when(promotionRepository.findFirstByServicioIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
        eq(7L), any(), any())).thenReturn(Optional.empty());
    when(coveragePricingService.quote(servicio, active, null)).thenReturn(expectedQuote);

    CoverageQuoteDTO result = patientCoverageService.quoteForPatient(patientUuid, 7L);

    assertThat(result).isSameAs(expectedQuote);
  }

  @Test
  void quoteForPatient_passesNullCoverage_whenPatientHasNoneActive() {
    Servicio servicio = Servicio.builder().id(7L).name("Consulta").price(50f).build();
    CoverageQuoteDTO expectedQuote = CoverageQuoteDTO.builder().servicioId(7L).hasCoverage(false).build();
    when(serviceRepository.findById(7L)).thenReturn(Optional.of(servicio));
    when(patientCoverageRepository.findFirstByPatientUuidAndActiveTrue(patientUuid))
        .thenReturn(Optional.empty());
    when(promotionRepository.findFirstByServicioIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
        eq(7L), any(), any())).thenReturn(Optional.empty());
    when(coveragePricingService.quote(servicio, null, null)).thenReturn(expectedQuote);

    CoverageQuoteDTO result = patientCoverageService.quoteForPatient(patientUuid, 7L);

    assertThat(result.isHasCoverage()).isFalse();
    verify(coveragePricingService).quote(servicio, null, null);
  }

  @Test
  void quoteForPatient_passesTheCurrentlyActivePromotion_whenOneExistsForTheService() {
    Servicio servicio = Servicio.builder().id(7L).name("Consulta").price(50f).build();
    Promotion activePromotion = Promotion.builder().id(3L).servicio(servicio).name("Promo").build();
    CoverageQuoteDTO expectedQuote = CoverageQuoteDTO.builder().servicioId(7L).build();
    when(serviceRepository.findById(7L)).thenReturn(Optional.of(servicio));
    when(patientCoverageRepository.findFirstByPatientUuidAndActiveTrue(patientUuid))
        .thenReturn(Optional.empty());
    when(promotionRepository.findFirstByServicioIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
        eq(7L), any(), any())).thenReturn(Optional.of(activePromotion));
    when(coveragePricingService.quote(servicio, null, activePromotion)).thenReturn(expectedQuote);

    CoverageQuoteDTO result = patientCoverageService.quoteForPatient(patientUuid, 7L);

    assertThat(result).isSameAs(expectedQuote);
    verify(coveragePricingService).quote(servicio, null, activePromotion);
  }

  @Test
  void quoteForPatient_throws_whenServicioNotFound() {
    when(serviceRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> patientCoverageService.quoteForPatient(patientUuid, 999L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Servicio no encontrado");
  }

  private Authentication staffAuth() {
    return new UsernamePasswordAuthenticationToken(
        UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
  }
}
