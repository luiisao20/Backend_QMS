package com.devluis.services;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devluis.dto.CoverageQuoteDTO;
import com.devluis.dto.CoveragePlanDTO;
import com.devluis.dto.InsurerDTO;
import com.devluis.dto.PatientCoverageDTO;
import com.devluis.dto.PatientDTO;
import com.devluis.entity.CoveragePlan;
import com.devluis.entity.Patient;
import com.devluis.entity.PatientCoverage;
import com.devluis.entity.Promotion;
import com.devluis.entity.Servicio;
import com.devluis.repository.CoveragePlanRepository;
import com.devluis.repository.PatientCoverageRepository;
import com.devluis.repository.PatientRepository;
import com.devluis.repository.PromotionRepository;
import com.devluis.repository.ServiceRepository;

import lombok.Data;

@Service
@Data
public class PatientCoverageService {
  private final PatientCoverageRepository patientCoverageRepository;
  private final PatientRepository patientRepository;
  private final CoveragePlanRepository coveragePlanRepository;
  private final ServiceRepository serviceRepository;
  private final PromotionRepository promotionRepository;
  private final PatientCoverageAccessGuard patientCoverageAccessGuard;
  private final CoveragePricingService coveragePricingService;

  @Transactional
  public PatientCoverageDTO create(PatientCoverageDTO dto) {
    Patient patient = resolvePatient(dto);
    CoveragePlan plan = resolvePlan(dto);
    validateDateRange(dto);

    PatientCoverage coverage = PatientCoverage.builder()
        .patient(patient)
        .plan(plan)
        .policyNumber(dto.getPolicyNumber())
        .validFrom(dto.getValidFrom())
        .validUntil(dto.getValidUntil())
        .active(Boolean.TRUE.equals(dto.getActive()))
        .build();

    PatientCoverage saved = patientCoverageRepository.save(coverage);
    if (saved.isActive()) {
      deactivateOtherActiveCoverages(patient.getUuid(), saved.getId());
    }
    return mapToDTO(saved);
  }

  // Backs both GET /api/patient-coverages/me and the staff
  // GET /api/patients/{patientId}/coverages screen — the only difference
  // between them is WHERE the controller sourced patientUuid from (the JWT
  // vs a path variable); both are pre-scoped to one patient, so neither
  // needs a per-record guard here (the staff route is already gated to
  // ROLE_EMPLOYEE/ROLE_ADMIN at the URL level in GlobalConfig).
  public List<PatientCoverageDTO> listForPatient(UUID patientUuid) {
    return patientCoverageRepository.findByPatientUuidOrderByValidFromDesc(patientUuid)
        .stream().map(this::mapToDTO).toList();
  }

  public PatientCoverageDTO getById(Long id, Authentication auth) {
    PatientCoverage coverage = findByIdOrThrow(id);
    patientCoverageAccessGuard.assertCanAccessCoverage(auth, resolvePatientUuid(coverage));
    return mapToDTO(coverage);
  }

  @Transactional
  public PatientCoverageDTO update(Long id, PatientCoverageDTO dto) {
    PatientCoverage coverage = findByIdOrThrow(id);
    Patient patient = resolvePatient(dto);
    CoveragePlan plan = resolvePlan(dto);
    validateDateRange(dto);

    coverage.setPatient(patient);
    coverage.setPlan(plan);
    coverage.setPolicyNumber(dto.getPolicyNumber());
    coverage.setValidFrom(dto.getValidFrom());
    coverage.setValidUntil(dto.getValidUntil());
    coverage.setActive(Boolean.TRUE.equals(dto.getActive()));

    PatientCoverage updated = patientCoverageRepository.save(coverage);
    if (updated.isActive()) {
      deactivateOtherActiveCoverages(patient.getUuid(), updated.getId());
    }
    return mapToDTO(updated);
  }

  public void delete(Long id) {
    if (!patientCoverageRepository.existsById(id)) {
      throw new RuntimeException("Cobertura no encontrada");
    }
    patientCoverageRepository.deleteById(id);
  }

  // GET /api/patient-coverages/me/quote — what the patient would pay for
  // servicioId right now, using their currently active coverage (or none)
  // AND the service's currently active Promotion (or none) — both flow
  // through the same CoveragePricingService path, never a parallel one.
  public CoverageQuoteDTO quoteForPatient(UUID patientUuid, Long servicioId) {
    Servicio servicio = serviceRepository.findById(servicioId)
        .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));
    PatientCoverage active = patientCoverageRepository
        .findFirstByPatientUuidAndActiveTrue(patientUuid)
        .orElse(null);
    LocalDate today = LocalDate.now();
    Promotion activePromotion = promotionRepository
        .findFirstByServicioIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(servicioId, today, today)
        .orElse(null);
    return coveragePricingService.quote(servicio, active, activePromotion);
  }

  private void deactivateOtherActiveCoverages(UUID patientUuid, Long keepId) {
    List<PatientCoverage> others =
        patientCoverageRepository.findByPatientUuidAndActiveTrueAndIdNot(patientUuid, keepId);
    if (others.isEmpty()) {
      return;
    }
    others.forEach(o -> o.setActive(false));
    patientCoverageRepository.saveAll(others);
  }

  private PatientCoverage findByIdOrThrow(Long id) {
    return patientCoverageRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Cobertura no encontrada"));
  }

  private Patient resolvePatient(PatientCoverageDTO dto) {
    return patientRepository.findById(dto.getPatient().getUuid())
        .orElseThrow(() -> new RuntimeException("Paciente no encontrado"));
  }

  private CoveragePlan resolvePlan(PatientCoverageDTO dto) {
    return coveragePlanRepository.findById(dto.getPlan().getId())
        .orElseThrow(() -> new RuntimeException("Plan de cobertura no encontrado"));
  }

  private UUID resolvePatientUuid(PatientCoverage coverage) {
    return coverage.getPatient() != null ? coverage.getPatient().getUuid() : null;
  }

  private void validateDateRange(PatientCoverageDTO dto) {
    if (dto.getValidUntil() != null && dto.getValidUntil().isBefore(dto.getValidFrom())) {
      throw new RuntimeException("La fecha de fin de vigencia no puede ser anterior a la fecha de inicio");
    }
  }

  private PatientCoverageDTO mapToDTO(PatientCoverage entity) {
    PatientDTO patientDTO = null;
    if (entity.getPatient() != null) {
      patientDTO = PatientDTO.builder()
          .uuid(entity.getPatient().getUuid())
          .firstName(entity.getPatient().getFirstName())
          .lastName(entity.getPatient().getLastName())
          .build();
    }

    CoveragePlanDTO planDTO = null;
    if (entity.getPlan() != null) {
      InsurerDTO insurerDTO = null;
      if (entity.getPlan().getInsurer() != null) {
        insurerDTO = InsurerDTO.builder()
            .id(entity.getPlan().getInsurer().getId())
            .name(entity.getPlan().getInsurer().getName())
            .type(entity.getPlan().getInsurer().getType())
            .build();
      }
      planDTO = CoveragePlanDTO.builder()
          .id(entity.getPlan().getId())
          .insurer(insurerDTO)
          .name(entity.getPlan().getName())
          .coveragePercentage(entity.getPlan().getCoveragePercentage())
          .copayAmount(entity.getPlan().getCopayAmount())
          .build();
    }

    return PatientCoverageDTO.builder()
        .id(entity.getId())
        .patient(patientDTO)
        .plan(planDTO)
        .policyNumber(entity.getPolicyNumber())
        .validFrom(entity.getValidFrom())
        .validUntil(entity.getValidUntil())
        .active(entity.isActive())
        .createdAt(entity.getCreatedAt())
        .build();
  }
}
