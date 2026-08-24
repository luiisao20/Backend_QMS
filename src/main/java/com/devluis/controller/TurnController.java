package com.devluis.controller;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.devluis.dto.TurnDTO;
import com.devluis.services.TurnService;

import lombok.Data;

@RestController
@RequestMapping("/api/turns")
@Data
public class TurnController {

  private final TurnService turnService;

  @PostMapping
  public ResponseEntity<TurnDTO> create(@RequestBody TurnDTO dto, Authentication auth) {
    return new ResponseEntity<>(turnService.create(dto, auth.getName()), HttpStatus.CREATED);
  }

  @PostMapping("/staff")
  public ResponseEntity<TurnDTO> createByStaff(@RequestBody TurnDTO dto, Authentication auth) {
    return new ResponseEntity<>(turnService.createByStaff(dto, auth.getName()), HttpStatus.CREATED);
  }

  @GetMapping
  public Page<TurnDTO> getAll(@PageableDefault(size = 10) Pageable pageable) {
    return turnService.getAll(pageable);
  }

  @GetMapping("/me")
  public ResponseEntity<Page<TurnDTO>> getMyTurns(
      @RequestParam(required = false) com.devluis.types.TurnStatus status,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
      @PageableDefault(size = 10) Pageable pageable,
      Authentication auth) {

    UUID patientUuid = UUID.fromString(auth.getName());
    Page<TurnDTO> myTurns = turnService.getTurnsForPatient(patientUuid, status, from, to, pageable);
    return ResponseEntity.ok(myTurns);
  }

  @GetMapping("/patient/{patientId}")
  public ResponseEntity<Page<TurnDTO>> getTurnsByPatient(
      @PathVariable UUID patientId,
      @RequestParam(required = false) com.devluis.types.TurnStatus status,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
      @PageableDefault(size = 10) Pageable pageable) {
    Page<TurnDTO> patientTurns = turnService.getTurnsForPatient(patientId, status, from, to, pageable);
    return ResponseEntity.ok(patientTurns);
  }

  @GetMapping("/{id}")
  public ResponseEntity<TurnDTO> getById(@PathVariable Long id) {
    return ResponseEntity.ok(turnService.getById(id));
  }

  @PutMapping("/{id}/treated")
  public ResponseEntity<?> markAsTreated(@PathVariable Long id, Authentication auth) {
    try {
      return ResponseEntity.ok(turnService.markAsTreated(id, auth.getName()));
    } catch (RuntimeException e) {
      if (e.getMessage().contains("permisos")) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
      }
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
  }

  @PutMapping("/{id}/cancelled")
  public ResponseEntity<?> cancelTurn(@PathVariable Long id, Authentication auth) {
    try {
      return ResponseEntity.ok(turnService.cancelTurn(id, auth.getName()));
    } catch (RuntimeException e) {
      if (e.getMessage().contains("permisos")) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
      }
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
  }
}
