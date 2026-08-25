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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.devluis.dto.PrescriptionDTO;
import com.devluis.services.PrescriptionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Same shape/reasoning as {@link EncounterController} (no class-level
 * {@code @RequestMapping}, coarse {@code @PreAuthorize} + fine-grained
 * {@link com.devluis.services.ClinicalAccessGuard} underneath). No
 * {@code @PutMapping}/{@code @DeleteMapping}: a Prescription is immutable
 * once issued — see the Prescription entity and apply report.
 */
@RestController
@RequiredArgsConstructor
public class PrescriptionController {

  private final PrescriptionService prescriptionService;

  @PreAuthorize("hasAnyAuthority('ROLE_DOCTOR', 'ROLE_ADMIN')")
  @PostMapping("/api/prescriptions")
  public ResponseEntity<PrescriptionDTO> create(@Valid @RequestBody PrescriptionDTO dto, Authentication auth) {
    return new ResponseEntity<>(prescriptionService.create(dto, auth), HttpStatus.CREATED);
  }

  @PreAuthorize("hasAnyAuthority('ROLE_DOCTOR', 'ROLE_ADMIN')")
  @GetMapping("/api/prescriptions/{id}")
  public ResponseEntity<PrescriptionDTO> getById(@PathVariable Long id, Authentication auth) {
    return ResponseEntity.ok(prescriptionService.getById(id, auth));
  }

  // Flutter's own prescriptions. Same "/me" idiom as GET /api/encounters/me.
  @GetMapping("/api/prescriptions/me")
  public ResponseEntity<Page<PrescriptionDTO>> getMyPrescriptions(
      @PageableDefault(size = 10) Pageable pageable, Authentication auth) {
    UUID patientUuid = UUID.fromString(auth.getName());
    return ResponseEntity.ok(prescriptionService.getMyPrescriptions(patientUuid, auth, pageable));
  }

  // The "pacientes/recetas" admin/staff screen.
  @PreAuthorize("hasAnyAuthority('ROLE_DOCTOR', 'ROLE_ADMIN')")
  @GetMapping("/api/patients/{patientId}/prescriptions")
  public ResponseEntity<Page<PrescriptionDTO>> getHistoryForPatient(
      @PathVariable UUID patientId,
      @PageableDefault(size = 10) Pageable pageable,
      Authentication auth) {
    return ResponseEntity.ok(prescriptionService.getHistoryForPatient(patientId, auth, pageable));
  }
}
