package com.devluis.controller;

import java.util.UUID;

import com.devluis.dto.ScheduleTemplateDTO;
import com.devluis.services.ScheduleTemplateService;

import jakarta.validation.Valid;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// "administracion/horarios": weekly recurring generation patterns
// (ScheduleTemplate). GET (bare list) is public — same reasoning as GET
// /api/holidays and GET /api/branding: a landing page needs to derive "Lunes
// a Viernes 08:00-17:00" from these rows WITHOUT authenticating first — see
// GlobalConfig. GET /{id} and writes follow the same tiers as every other
// admin-managed catalogue in this codebase.
@RestController
@RequestMapping("/api/schedule-templates")
@Data
public class ScheduleTemplateController {

  private final ScheduleTemplateService scheduleTemplateService;

  @PostMapping("/save")
  public ResponseEntity<ScheduleTemplateDTO> create(@Valid @RequestBody ScheduleTemplateDTO dto) {
    return new ResponseEntity<>(scheduleTemplateService.create(dto), HttpStatus.CREATED);
  }

  @GetMapping
  public Page<ScheduleTemplateDTO> getAll(
      @RequestParam(required = false) Long stablishmentId,
      @RequestParam(required = false) Long serviceId,
      @RequestParam(required = false) UUID doctorId,
      @PageableDefault(size = 10) Pageable pageable) {
    return scheduleTemplateService.getAll(stablishmentId, serviceId, doctorId, pageable);
  }

  @GetMapping("/{id}")
  public ResponseEntity<ScheduleTemplateDTO> getById(@PathVariable Long id) {
    return ResponseEntity.ok(scheduleTemplateService.getById(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ScheduleTemplateDTO> update(@PathVariable Long id, @Valid @RequestBody ScheduleTemplateDTO dto) {
    return ResponseEntity.ok(scheduleTemplateService.update(id, dto));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    scheduleTemplateService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
