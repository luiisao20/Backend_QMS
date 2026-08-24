package com.devluis.controller;

import com.devluis.dto.DoctorDTO;
import com.devluis.dto.ScheduleDTO;
import com.devluis.dto.ServicioDTO;
import com.devluis.dto.StablishmentDTO;
import com.devluis.services.ServicioService;
import com.devluis.types.ScheduleStatus;
import jakarta.validation.Valid;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/services")
@Data
public class ServicioController {

  private final ServicioService servicioService;

  @PostMapping
  public ResponseEntity<ServicioDTO> create(@Valid @RequestBody ServicioDTO dto) {
    return new ResponseEntity<>(servicioService.create(dto), HttpStatus.CREATED);
  }

  @GetMapping("/my-services")
  public ResponseEntity<List<ServicioDTO>> getMyServices(Authentication authentication) {
    if (authentication == null || authentication.getName() == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    UUID doctorId = UUID.fromString(authentication.getName());
    return ResponseEntity.ok(servicioService.getMyServices(doctorId));
  }

  @GetMapping
  public Page<ServicioDTO> getAll(
      @RequestParam(required = false) String name,
      @PageableDefault(size = 10) Pageable pageable) {
    return servicioService.getAll(name, pageable);
  }

  @GetMapping("/{id}")
  public ResponseEntity<ServicioDTO> getById(@PathVariable Long id) {
    return ResponseEntity.ok(servicioService.getById(id));
  }

  @GetMapping("/{id}/doctors")
  public ResponseEntity<Page<DoctorDTO>> getDoctorsByService(
      @PathVariable Long id,
      @RequestParam(required = false) String name,
      @PageableDefault(size = 10) Pageable pageable) {
    return ResponseEntity.ok(servicioService.getDoctorsByService(id, name, pageable));
  }

  @GetMapping("/{id}/stablishments")
  public ResponseEntity<Page<StablishmentDTO>> getStablishmentsByService(
      @PathVariable Long id,
      @RequestParam(required = false) String name,
      @PageableDefault(size = 10) Pageable pageable) {
    return ResponseEntity.ok(servicioService.getStablishmentsByService(id, name, pageable));
  }

  @GetMapping("/{id}/schedules")
  public ResponseEntity<Page<ScheduleDTO>> getSchedulesByService(
      @PathVariable Long id,
      @RequestParam(required = false) Long stablishmentId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestParam(required = false) ScheduleStatus status,
      @PageableDefault(size = 10, sort = {"date", "hour"}, direction = Direction.ASC) Pageable pageable) {
    return ResponseEntity.ok(servicioService.getSchedulesByService(id, stablishmentId, date, status, pageable));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ServicioDTO> update(@PathVariable Long id, @Valid @RequestBody ServicioDTO dto) {
    return ResponseEntity.ok(servicioService.update(id, dto));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    servicioService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
