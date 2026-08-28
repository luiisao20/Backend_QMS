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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.devluis.dto.TurnDTO;
import com.devluis.dto.TurnDailyCountsDTO;
import com.devluis.services.TurnService;
import com.devluis.types.TurnStatus;

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
  public Page<TurnDTO> getAll(
      @RequestParam(required = false) Long stablishmentId,
      @RequestParam(required = false) UUID doctorId,
      @RequestParam(required = false) Long serviceId,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date,
      @RequestParam(required = false) TurnStatus status,
      @PageableDefault(size = 10) Pageable pageable) {
    return turnService.getAll(stablishmentId, doctorId, serviceId, date, status, pageable);
  }

  /**
   * Conteos del dia para las tarjetas del panel: por sede y por servicio.
   *
   * Va ANTES de @GetMapping("/{id}") en el archivo por costumbre, pero no
   * depende de eso: "daily-counts" no es un Long, asi que la ruta con
   * parametro no lo captura ni aunque estuviera primero.
   *
   * `date` es opcional y cae a hoy. La tarjeta que el operador mira al abrir el
   * panel es la de la jornada en curso; pedirle la fecha para el caso normal
   * seria pedirle que escriba lo que ya sabemos.
   */
  @GetMapping("/daily-counts")
  public ResponseEntity<TurnDailyCountsDTO> getDailyCounts(
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
    return ResponseEntity.ok(turnService.getDailyCounts(date != null ? date : LocalDate.now()));
  }

  @GetMapping("/me")
  public ResponseEntity<Page<TurnDTO>> getMyTurns(
      @RequestParam(required = false) TurnStatus status,
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

  /**
   * Check-in: the patient arrived and registered at the counter. Staff-only.
   * "/api/turns/**" only requires authentication under GlobalConfig's general
   * matcher, so without this the endpoint would be reachable by the turn's
   * own patient. Mirrors the roles GlobalConfig hardcodes for
   * "/api/turns/*\/reassign" and "/api/turns/*\/staff-cancel".
   */
  @PutMapping("/{id}/waiting")
  public ResponseEntity<?> markAsWaiting(@PathVariable Long id, Authentication auth) {
    try {
      return ResponseEntity.ok(turnService.markAsWaiting(id, auth.getName()));
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Start attention: the patient is called in from the waiting room.
   * Staff-only for the same reason as {@link #markAsWaiting} above.
   */
  @PutMapping("/{id}/in-treatment")
  public ResponseEntity<?> markAsInTreatment(@PathVariable Long id,
      @RequestBody(required = false) com.devluis.dto.CallTurnDTO body,
      Authentication auth) {
    try {
      Long consultorioId = body == null ? null : body.getConsultorioId();
      return ResponseEntity.ok(turnService.markAsInTreatment(id, consultorioId, auth.getName()));
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
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

  @PutMapping("/{id}/treated/admin")
  public ResponseEntity<?> markAsTreatedAdmin(@PathVariable Long id) {
    try {
      return ResponseEntity.ok(turnService.markAsTreatedAdmin(id));
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

  @PutMapping("/{id}/reassign")
  public ResponseEntity<?> reassignTurn(
      @PathVariable Long id,
      @jakarta.validation.Valid @RequestBody com.devluis.types.ReassignTurnBody body,
      Authentication auth) {
    try {
      return ResponseEntity.ok(turnService.reassignTurn(id, body.getScheduleId(), auth.getName()));
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
  }

  @PutMapping("/{id}/staff-cancel")
  public ResponseEntity<?> cancelTurnByStaff(
      @PathVariable Long id,
      @RequestBody(required = false) com.devluis.types.CancelTurnBody body,
      Authentication auth) {
    try {
      String reason = body != null ? body.getReason() : null;
      return ResponseEntity.ok(turnService.cancelTurnByStaff(id, auth.getName(), reason));
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
  }
}
