package com.devluis.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.devluis.dto.EncounterDTO;
import com.devluis.services.EncounterService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * No class-level {@code @RequestMapping}: routes span two resource roots
 * ("/api/encounters/**" and the patient sub-resource
 * "/api/patients/{patientId}/encounters"), so each method carries its own
 * full path instead of forcing a nested-resource read under a Patient
 * controller that would otherwise need EncounterService injected into it
 * just for this one route.
 *
 * Coarse role gating mirrors {@code PatientController#getPatient}:
 * {@code @PreAuthorize} keeps ROLE_EMPLOYEE and ROLE_PATIENT off every
 * staff-only route below. The fine-grained "is this THE treating doctor of
 * THIS record" rule is not expressible as a static role check — it lives in
 * {@link com.devluis.services.ClinicalAccessGuard}, called from
 * {@link EncounterService}. "/me" is deliberately NOT gated by
 * {@code @PreAuthorize}: any authenticated role may call it (mirrors
 * {@code TurnController#getMyTurns}), because it only ever returns the
 * caller's OWN uuid's data.
 */
@RestController
@RequiredArgsConstructor
public class EncounterController {

  private final EncounterService encounterService;

  @PreAuthorize("hasAnyAuthority('ROLE_DOCTOR', 'ROLE_ADMIN')")
  @PostMapping("/api/encounters")
  public ResponseEntity<EncounterDTO> create(@Valid @RequestBody EncounterDTO dto, Authentication auth) {
    return new ResponseEntity<>(encounterService.create(dto, auth), HttpStatus.CREATED);
  }

  @PreAuthorize("hasAnyAuthority('ROLE_DOCTOR', 'ROLE_ADMIN')")
  @GetMapping("/api/encounters/{id}")
  public ResponseEntity<EncounterDTO> getById(@PathVariable Long id, Authentication auth) {
    return ResponseEntity.ok(encounterService.getById(id, auth));
  }

  // Flutter's clinical-history screen. Same "/me" idiom as GET
  // /api/turns/me and GET /api/patients/me.
  @GetMapping("/api/encounters/me")
  public ResponseEntity<Page<EncounterDTO>> getMyHistory(
      @PageableDefault(size = 10) Pageable pageable, Authentication auth) {
    UUID patientUuid = UUID.fromString(auth.getName());
    return ResponseEntity.ok(encounterService.getMyHistory(patientUuid, auth, pageable));
  }

  // The "pacientes/historial-clinico" admin/staff screen.
  @PreAuthorize("hasAnyAuthority('ROLE_DOCTOR', 'ROLE_ADMIN')")
  @GetMapping("/api/patients/{patientId}/encounters")
  public ResponseEntity<Page<EncounterDTO>> getHistoryForPatient(
      @PathVariable UUID patientId,
      @PageableDefault(size = 10) Pageable pageable,
      Authentication auth) {
    return ResponseEntity.ok(encounterService.getHistoryForPatient(patientId, auth, pageable));
  }

  @PreAuthorize("hasAnyAuthority('ROLE_DOCTOR', 'ROLE_ADMIN')")
  @PutMapping("/api/encounters/{id}")
  public ResponseEntity<EncounterDTO> update(
      @PathVariable Long id, @Valid @RequestBody EncounterDTO dto, Authentication auth) {
    return ResponseEntity.ok(encounterService.update(id, dto, auth));
  }

  // No @DeleteMapping: an Encounter is a legal clinical record and is never
  // hard-deletable through this API. See apply report.
}
