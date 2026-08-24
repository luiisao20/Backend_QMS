package com.devluis.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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
      return ResponseEntity.ok(java.util.Map.of("Message", "Contraseña actualizada exitosamente"));
    } catch (RuntimeException e) {
      return Helper.getResponseMessage(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

}
