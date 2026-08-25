package com.devluis.services;

import java.util.Arrays;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import com.devluis.dto.ClinicalAccessLogDTO;
import com.devluis.entity.ClinicalAccessLog;
import com.devluis.repository.ClinicalAccessLogRepository;
import com.devluis.types.ClinicalResourceType;
import com.devluis.types.Role;

import lombok.Data;

/**
 * Records WHO read WHICH patient's clinical data, for reportes/auditoria-hc.
 * This is an audit of ACCESS, not of edits — create()/update() on
 * Encounter/Prescription deliberately never call {@link #record}. Recording
 * every read here is fully practical (every clinical read in this system
 * goes through EncounterService/PrescriptionService — there is no other read
 * path), so all six read endpoints call this, including a patient reading
 * their own data via "/me": "who read this patient's history" is still a
 * true, useful question even when the answer is "the patient themselves".
 */
@Service
@Data
public class ClinicalAccessLogService {
  private final ClinicalAccessLogRepository clinicalAccessLogRepository;

  public void record(UUID patientUuid, Authentication auth, ClinicalResourceType resourceType, Long resourceId) {
    ClinicalAccessLog log = ClinicalAccessLog.builder()
        .patientUuid(patientUuid)
        .accessedByUuid(UUID.fromString(auth.getName()))
        .accessedByRole(resolveRole(auth))
        .resourceType(resourceType)
        .resourceId(resourceId)
        .build();
    clinicalAccessLogRepository.save(log);
  }

  public Page<ClinicalAccessLogDTO> getAll(UUID patientUuid, Pageable pageable) {
    Page<ClinicalAccessLog> page = patientUuid != null
        ? clinicalAccessLogRepository.findByPatientUuid(patientUuid, pageable)
        : clinicalAccessLogRepository.findAll(pageable);
    return page.map(this::mapToDTO);
  }

  // Only the first authority that matches a known Role constant is recorded.
  // Every principal type this system issues a JWT for carries exactly one
  // Role authority today, so this is a disclosed simplification, not a
  // silent gap.
  private Role resolveRole(Authentication auth) {
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .flatMap(authority -> Arrays.stream(Role.values()).filter(role -> role.name().equals(authority)))
        .findFirst()
        .orElse(null);
  }

  private ClinicalAccessLogDTO mapToDTO(ClinicalAccessLog entity) {
    return ClinicalAccessLogDTO.builder()
        .id(entity.getId())
        .patientUuid(entity.getPatientUuid())
        .accessedByUuid(entity.getAccessedByUuid())
        .accessedByRole(entity.getAccessedByRole())
        .resourceType(entity.getResourceType())
        .resourceId(entity.getResourceId())
        .accessedAt(entity.getAccessedAt())
        .build();
  }
}
