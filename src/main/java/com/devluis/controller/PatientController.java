package com.devluis.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devluis.dto.PatientDTO;
import com.devluis.services.PatientService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/patients")
@RequiredArgsConstructor
public class PatientController {
  
  private final PatientService patientService;

  @GetMapping
  public ResponseEntity<Page<PatientDTO>> getAllPatients(Pageable pageable) {
    Page<PatientDTO> patients = patientService.getAll(pageable);
    return ResponseEntity.ok(patients);
  }
  @org.springframework.web.bind.annotation.PutMapping("/me")
  public ResponseEntity<?> updateMyProfile(
      @org.springframework.web.bind.annotation.RequestBody PatientDTO patientDTO,
      org.springframework.security.core.Authentication auth) {
    try {
      java.util.UUID uuid = java.util.UUID.fromString(auth.getName());
      PatientDTO updated = patientService.updatePatient(uuid, patientDTO);
      return ResponseEntity.ok(updated);
    } catch (RuntimeException e) {
      return com.devluis.utils.Helper.getResponseMessage(e.getMessage(), org.springframework.http.HttpStatus.BAD_REQUEST);
    }
  }

}
