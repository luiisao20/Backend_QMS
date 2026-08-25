package com.devluis.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devluis.dto.CoverageQuoteDTO;
import com.devluis.dto.PatientCoverageDTO;
import com.devluis.services.PatientCoverageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * No class-level {@code @RequestMapping}: routes span two resource roots
 * ("/api/patient-coverages/**" and the staff sub-resource
 * "/api/patients/{patientId}/coverages") — same two-root shape as
 * {@link EncounterController}.
 *
 * <p>Coarse gating: staff CRUD (create/update/delete/list-for-patient)
 * requires ROLE_EMPLOYEE or ROLE_ADMIN — insurance/billing is front-desk
 * work in this codebase, not clinical, so ROLE_DOCTOR is deliberately
 * excluded (see apply report). "/me" and "/me/quote" are reachable by ANY
 * authenticated role, same "/me" idiom as GET /api/turns/me — they only
 * ever resolve the caller's own uuid, no separate authorization check
 * needed. GET "/{id}" is ALSO reachable by any authenticated role at the URL
 * level (GlobalConfig has no explicit matcher for it — it falls through to
 * ".anyRequest().authenticated()"), but {@link PatientCoverageService}
 * enforces per-record ownership via {@link com.devluis.services.PatientCoverageAccessGuard}.
 * This is a deliberate deviation from EncounterController's stricter
 * "/{id}" (staff-only there): unlike a clinical note, a coverage record's
 * own DTO is exactly what a patient is allowed to see about themselves, so
 * gating by record ownership instead of blocking the route outright lets
 * "/{id}" double as both the staff detail-by-id call and a patient
 * re-fetching one of their own records.
 */
@RestController
@RequiredArgsConstructor
public class PatientCoverageController {

  private final PatientCoverageService patientCoverageService;

  @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYEE', 'ROLE_ADMIN')")
  @PostMapping("/api/patient-coverages")
  public ResponseEntity<PatientCoverageDTO> create(@Valid @RequestBody PatientCoverageDTO dto) {
    return new ResponseEntity<>(patientCoverageService.create(dto), HttpStatus.CREATED);
  }

  // Flutter's "Cobertura" group in personal_info_screen.dart.
  @GetMapping("/api/patient-coverages/me")
  public ResponseEntity<List<PatientCoverageDTO>> getMyCoverages(Authentication auth) {
    UUID patientUuid = UUID.fromString(auth.getName());
    return ResponseEntity.ok(patientCoverageService.listForPatient(patientUuid));
  }

  // What the caller would pay for a given service today, factoring in their
  // currently active coverage (or none).
  @GetMapping("/api/patient-coverages/me/quote")
  public ResponseEntity<CoverageQuoteDTO> quoteForMe(
      @RequestParam Long servicioId, Authentication auth) {
    UUID patientUuid = UUID.fromString(auth.getName());
    return ResponseEntity.ok(patientCoverageService.quoteForPatient(patientUuid, servicioId));
  }

  @GetMapping("/api/patient-coverages/{id}")
  public ResponseEntity<PatientCoverageDTO> getById(@PathVariable Long id, Authentication auth) {
    return ResponseEntity.ok(patientCoverageService.getById(id, auth));
  }

  // The "administracion/planes-de-cobertura"-adjacent staff screen: this
  // patient's coverage history.
  @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYEE', 'ROLE_ADMIN')")
  @GetMapping("/api/patients/{patientId}/coverages")
  public ResponseEntity<List<PatientCoverageDTO>> getForPatient(@PathVariable UUID patientId) {
    return ResponseEntity.ok(patientCoverageService.listForPatient(patientId));
  }

  @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYEE', 'ROLE_ADMIN')")
  @PutMapping("/api/patient-coverages/{id}")
  public ResponseEntity<PatientCoverageDTO> update(
      @PathVariable Long id, @Valid @RequestBody PatientCoverageDTO dto) {
    return ResponseEntity.ok(patientCoverageService.update(id, dto));
  }

  @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYEE', 'ROLE_ADMIN')")
  @DeleteMapping("/api/patient-coverages/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    patientCoverageService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
