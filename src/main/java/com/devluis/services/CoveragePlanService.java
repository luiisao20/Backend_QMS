package com.devluis.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.devluis.dto.CoveragePlanDTO;
import com.devluis.dto.InsurerDTO;
import com.devluis.entity.CoveragePlan;
import com.devluis.entity.Insurer;
import com.devluis.repository.CoveragePlanRepository;
import com.devluis.repository.InsurerRepository;
import com.devluis.repository.PatientCoverageRepository;

import lombok.Data;

@Service
@Data
public class CoveragePlanService {
  private final CoveragePlanRepository coveragePlanRepository;
  private final InsurerRepository insurerRepository;
  private final PatientCoverageRepository patientCoverageRepository;

  public CoveragePlanDTO create(CoveragePlanDTO dto) {
    Insurer insurer = resolveInsurer(dto);

    CoveragePlan plan = CoveragePlan.builder()
        .insurer(insurer)
        .name(dto.getName())
        .coveragePercentage(dto.getCoveragePercentage())
        .copayAmount(dto.getCopayAmount())
        .build();

    CoveragePlan saved = coveragePlanRepository.save(plan);
    return mapToDTO(saved);
  }

  public Page<CoveragePlanDTO> getAll(Long insurerId, Pageable pageable) {
    if (insurerId != null) {
      return coveragePlanRepository.findByInsurerId(insurerId, pageable).map(this::mapToDTO);
    }
    return coveragePlanRepository.findAll(pageable).map(this::mapToDTO);
  }

  public Page<CoveragePlanDTO> getAll(Pageable pageable) {
    return getAll(null, pageable);
  }

  public CoveragePlanDTO getById(Long id) {
    return mapToDTO(findByIdOrThrow(id));
  }

  public CoveragePlanDTO update(Long id, CoveragePlanDTO dto) {
    CoveragePlan plan = findByIdOrThrow(id);
    Insurer insurer = resolveInsurer(dto);

    plan.setInsurer(insurer);
    plan.setName(dto.getName());
    plan.setCoveragePercentage(dto.getCoveragePercentage());
    plan.setCopayAmount(dto.getCopayAmount());

    CoveragePlan updated = coveragePlanRepository.save(plan);
    return mapToDTO(updated);
  }

  public void delete(Long id) {
    if (!coveragePlanRepository.existsById(id)) {
      throw new RuntimeException("Plan de cobertura no encontrado");
    }
    if (patientCoverageRepository.existsByPlanId(id)) {
      throw new RuntimeException(
          "No se puede eliminar el plan porque tiene coberturas de pacientes asociadas. Reasigne o elimine esas coberturas antes de eliminarlo.");
    }
    coveragePlanRepository.deleteById(id);
  }

  private CoveragePlan findByIdOrThrow(Long id) {
    return coveragePlanRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Plan de cobertura no encontrado"));
  }

  private Insurer resolveInsurer(CoveragePlanDTO dto) {
    return insurerRepository.findById(dto.getInsurer().getId())
        .orElseThrow(() -> new RuntimeException("Aseguradora no encontrada"));
  }

  private CoveragePlanDTO mapToDTO(CoveragePlan entity) {
    InsurerDTO insurerDTO = null;
    if (entity.getInsurer() != null) {
      insurerDTO = InsurerDTO.builder()
          .id(entity.getInsurer().getId())
          .name(entity.getInsurer().getName())
          .type(entity.getInsurer().getType())
          .build();
    }

    return CoveragePlanDTO.builder()
        .id(entity.getId())
        .insurer(insurerDTO)
        .name(entity.getName())
        .coveragePercentage(entity.getCoveragePercentage())
        .copayAmount(entity.getCopayAmount())
        .createdAt(entity.getCreatedAt())
        .build();
  }
}
