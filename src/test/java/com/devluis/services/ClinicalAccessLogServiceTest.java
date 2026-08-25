package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.devluis.dto.ClinicalAccessLogDTO;
import com.devluis.entity.ClinicalAccessLog;
import com.devluis.repository.ClinicalAccessLogRepository;
import com.devluis.types.ClinicalResourceType;
import com.devluis.types.Role;

/**
 * This is the "who READ this patient's history" ledger — an audit of clinical
 * ACCESS, not of edits (create/update on Encounter/Prescription never call
 * this service; see EncounterService/PrescriptionService and the apply
 * report for why that split is deliberate).
 */
@ExtendWith(MockitoExtension.class)
class ClinicalAccessLogServiceTest {

  @Mock
  private ClinicalAccessLogRepository clinicalAccessLogRepository;

  private ClinicalAccessLogService clinicalAccessLogService;

  @BeforeEach
  void setUp() {
    clinicalAccessLogService = new ClinicalAccessLogService(clinicalAccessLogRepository);
  }

  private Authentication authOf(UUID uuid, String role) {
    return new UsernamePasswordAuthenticationToken(uuid.toString(), null, List.of(new SimpleGrantedAuthority(role)));
  }

  @Test
  void record_savesOneLogEntry_withPatientRoleAndResourceCapturedFromTheCaller() {
    UUID patientUuid = UUID.randomUUID();
    UUID doctorUuid = UUID.randomUUID();
    Authentication doctorAuth = authOf(doctorUuid, "ROLE_DOCTOR");
    when(clinicalAccessLogRepository.save(any(ClinicalAccessLog.class))).thenAnswer(inv -> inv.getArgument(0));

    clinicalAccessLogService.record(patientUuid, doctorAuth, ClinicalResourceType.ENCOUNTER, 42L);

    ArgumentCaptor<ClinicalAccessLog> captor = ArgumentCaptor.forClass(ClinicalAccessLog.class);
    verify(clinicalAccessLogRepository).save(captor.capture());
    ClinicalAccessLog saved = captor.getValue();
    assertThat(saved.getPatientUuid()).isEqualTo(patientUuid);
    assertThat(saved.getAccessedByUuid()).isEqualTo(doctorUuid);
    assertThat(saved.getAccessedByRole()).isEqualTo(Role.ROLE_DOCTOR);
    assertThat(saved.getResourceType()).isEqualTo(ClinicalResourceType.ENCOUNTER);
    assertThat(saved.getResourceId()).isEqualTo(42L);
  }

  @Test
  void record_acceptsNullResourceId_forListReads() {
    UUID patientUuid = UUID.randomUUID();
    Authentication adminAuth = authOf(UUID.randomUUID(), "ROLE_ADMIN");
    when(clinicalAccessLogRepository.save(any(ClinicalAccessLog.class))).thenAnswer(inv -> inv.getArgument(0));

    clinicalAccessLogService.record(patientUuid, adminAuth, ClinicalResourceType.ENCOUNTER_LIST, null);

    ArgumentCaptor<ClinicalAccessLog> captor = ArgumentCaptor.forClass(ClinicalAccessLog.class);
    verify(clinicalAccessLogRepository).save(captor.capture());
    assertThat(captor.getValue().getResourceId()).isNull();
    assertThat(captor.getValue().getAccessedByRole()).isEqualTo(Role.ROLE_ADMIN);
  }

  @Test
  void getAll_filtersByPatientUuid_whenProvided() {
    UUID patientUuid = UUID.randomUUID();
    Pageable pageable = PageRequest.of(0, 10);
    ClinicalAccessLog entity = ClinicalAccessLog.builder()
        .id(1L).patientUuid(patientUuid).accessedByUuid(UUID.randomUUID())
        .accessedByRole(Role.ROLE_ADMIN).resourceType(ClinicalResourceType.ENCOUNTER).resourceId(1L)
        .build();
    when(clinicalAccessLogRepository.findByPatientUuid(eq(patientUuid), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(entity)));

    Page<ClinicalAccessLogDTO> result = clinicalAccessLogService.getAll(patientUuid, pageable);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getPatientUuid()).isEqualTo(patientUuid);
  }

  @Test
  void getAll_returnsEverything_whenNoPatientFilterProvided() {
    Pageable pageable = PageRequest.of(0, 10);
    when(clinicalAccessLogRepository.findAll(pageable)).thenReturn(Page.empty());

    Page<ClinicalAccessLogDTO> result = clinicalAccessLogService.getAll(null, pageable);

    assertThat(result.getContent()).isEmpty();
    verify(clinicalAccessLogRepository, org.mockito.Mockito.never()).findByPatientUuid(any(), any());
  }

  @Test
  void record_resolvesNullRole_whenCallerHasNoRecognizedRoleAuthority() {
    // Defensive edge case: should never happen with this system's real
    // tokens (every principal type carries exactly one Role authority), but
    // must not throw if it ever did — mirrors the DoctorDTO/@JsonInclude
    // house style of tolerating an absent-but-not-fatal field.
    UUID patientUuid = UUID.randomUUID();
    Authentication weirdAuth = mock(Authentication.class);
    when(weirdAuth.getName()).thenReturn(UUID.randomUUID().toString());
    when(weirdAuth.getAuthorities()).thenReturn(List.of());
    when(clinicalAccessLogRepository.save(any(ClinicalAccessLog.class))).thenAnswer(inv -> inv.getArgument(0));

    clinicalAccessLogService.record(patientUuid, weirdAuth, ClinicalResourceType.PRESCRIPTION, 1L);

    ArgumentCaptor<ClinicalAccessLog> captor = ArgumentCaptor.forClass(ClinicalAccessLog.class);
    verify(clinicalAccessLogRepository).save(captor.capture());
    assertThat(captor.getValue().getAccessedByRole()).isNull();
  }
}
