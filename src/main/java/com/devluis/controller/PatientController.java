package com.devluis.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devluis.dto.PatientDTO;
import com.devluis.services.PatientService;
import com.devluis.types.ChangePasswordBody;
import com.devluis.utils.Helper;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

  private final PatientService patientService;

  @GetMapping
  public ResponseEntity<Page<PatientDTO>> getAllPatients(
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String ci,
      Pageable pageable) {
    Page<PatientDTO> patients = patientService.getAll(name, ci, pageable);
    return ResponseEntity.ok(patients);
  }

  // "/me" is a literal path segment, so Spring matches it here instead of
  // "/{id}" below regardless of declaration order (exact segments always win
  // over path variables). See PatientControllerTest for the routing guard.
  @GetMapping("/me")
  public ResponseEntity<?> getMyProfile(Authentication auth) {
    try {
      UUID uuid = UUID.fromString(auth.getName());
      PatientDTO patient = patientService.getPatientById(uuid);
      return ResponseEntity.ok(patient);
    } catch (RuntimeException e) {
      return Helper.getResponseMessage(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  /**
   * Staff-only. Reading ANOTHER patient's full profile — email, ci, birthday,
   * address, phone, emergency contacts — is a staff action, so this mirrors the
   * rule GlobalConfig already applies to {@code /api/turns/patient/**}.
   *
   * <p>Without this, the route would inherit the config's blanket
   * {@code .authenticated()} for {@code /api/patients/**} and any signed-in
   * patient could read every other patient by UUID. A patient reading their own
   * profile uses {@code GET /me} instead.
   */
  @PreAuthorize("hasAnyAuthority('ROLE_DOCTOR', 'ROLE_EMPLOYEE', 'ROLE_ADMIN')")
  @GetMapping("/{id}")
  public ResponseEntity<PatientDTO> getPatient(@PathVariable UUID id) {
    return ResponseEntity.ok(patientService.getPatientById(id));
  }

  @PutMapping("/me")
  public ResponseEntity<?> updateMyProfile(
      @RequestBody PatientDTO patientDTO,
      Authentication auth) {
    try {
      UUID uuid = UUID.fromString(auth.getName());
      PatientDTO updated = patientService.updatePatient(uuid, patientDTO);
      return ResponseEntity.ok(updated);
    } catch (RuntimeException e) {
      return Helper.getResponseMessage(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @PutMapping("/change-password")
  public ResponseEntity<?> changeMyPassword(
      @Valid @RequestBody ChangePasswordBody body,
      Authentication auth) {
    try {
      if (!body.getPassword().equals(body.getRepeatedPassword())) {
        return Helper.getResponseMessage("Las contraseñas no coinciden", HttpStatus.BAD_REQUEST);
      }
      UUID uuid = UUID.fromString(auth.getName());
      patientService.updatePassword(uuid, body.getPassword());
      return ResponseEntity.ok(Map.of("Message", "Contraseña actualizada exitosamente"));
    } catch (RuntimeException e) {
      return Helper.getResponseMessage(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

}
