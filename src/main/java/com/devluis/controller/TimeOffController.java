package com.devluis.controller;

import com.devluis.dto.TimeOffDTO;
import com.devluis.services.TimeOffService;
import com.devluis.types.TimeOffKind;
import jakarta.validation.Valid;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Ausencias de doctores — las pantallas Vacaciones y Permisos del panel.
 *
 * UN CONTROLLER PARA LAS DOS PANTALLAS. Se separan con `?kind=`, no con dos
 * rutas: el recurso es el mismo y duplicar el endpoint duplicaría también las
 * validaciones de rango y de solapamiento, que son la parte que importa.
 */
@RestController
@RequestMapping("/api/time-off")
@Data
public class TimeOffController {

  private final TimeOffService timeOffService;

  @PostMapping
  public ResponseEntity<TimeOffDTO> create(@Valid @RequestBody TimeOffDTO dto) {
    return new ResponseEntity<>(timeOffService.create(dto), HttpStatus.CREATED);
  }

  @GetMapping
  public Page<TimeOffDTO> getAll(
      @RequestParam(required = false) UUID doctorId,
      @RequestParam(required = false) TimeOffKind kind,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
      @PageableDefault(size = 10) Pageable pageable) {
    return timeOffService.search(doctorId, kind, from, to, pageable);
  }

  @GetMapping("/{id}")
  public ResponseEntity<TimeOffDTO> getById(@PathVariable Long id) {
    return ResponseEntity.ok(timeOffService.getById(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<TimeOffDTO> update(@PathVariable Long id, @Valid @RequestBody TimeOffDTO dto) {
    return ResponseEntity.ok(timeOffService.update(id, dto));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    timeOffService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
