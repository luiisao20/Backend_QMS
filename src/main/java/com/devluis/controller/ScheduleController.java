package com.devluis.controller;

import com.devluis.dto.DoctorDTO;
import com.devluis.dto.ScheduleDTO;
import com.devluis.services.ScheduleService;
import com.devluis.types.GenerateSchedulesBody;
import com.devluis.types.GenerateSchedulesFromTemplateBody;
import com.devluis.types.ScheduleStatus;

import jakarta.validation.Valid;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/schedules")
@Data
public class ScheduleController {

  private final ScheduleService scheduleService;

  @PostMapping({ "", "/create" })
  public ResponseEntity<ScheduleDTO> create(@Valid @RequestBody ScheduleDTO dto) {
    return new ResponseEntity<>(scheduleService.create(dto), HttpStatus.CREATED);
  }

  @GetMapping
  public Page<ScheduleDTO> getAll(
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date,
      @RequestParam(required = false) Long stablishmentId,
      @RequestParam(required = false) UUID doctorId,
      @RequestParam(required = false) String doctorName,
      @RequestParam(required = false) Long serviceId,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
      @RequestParam(required = false) ScheduleStatus status,
      @PageableDefault(size = 10, sort = { "date", "hour" }, direction = Direction.ASC) Pageable pageable) {
    return scheduleService.getAll(date, stablishmentId, doctorId, doctorName, serviceId, from, to, status, pageable);
  }

  @GetMapping("/{id}")
  public ResponseEntity<ScheduleDTO> getById(@PathVariable Long id) {
    return ResponseEntity.ok(scheduleService.getById(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ScheduleDTO> update(@PathVariable Long id, @Valid @RequestBody ScheduleDTO dto) {
    return ResponseEntity.ok(scheduleService.update(id, dto));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    scheduleService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/bulk-delete")
  public ResponseEntity<Void> bulkDelete(@RequestBody List<Long> ids) {
    scheduleService.bulkDelete(ids);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/bulk-update-status")
  public ResponseEntity<Void> bulkUpdateStatus(@RequestParam ScheduleStatus status, @RequestBody List<Long> ids) {
    scheduleService.bulkUpdateStatus(status, ids);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/my-schedules")
  public Page<ScheduleDTO> getMySchedules(
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date,
      @RequestParam(required = false) Long stablishmentId,
      @RequestParam(required = false) Long serviceId,
      @RequestParam(required = false) ScheduleStatus status,
      @PageableDefault(size = 10, sort = { "date", "hour" }, direction = Direction.ASC) Pageable pageable,
      org.springframework.security.core.Authentication auth) {
    UUID doctorId = UUID.fromString(auth.getName());
    return scheduleService.getAll(date, stablishmentId, doctorId, null, serviceId, null, null, status, pageable);
  }

  @PostMapping("/my-schedules/create")
  public ResponseEntity<ScheduleDTO> createMySchedule(@Valid @RequestBody ScheduleDTO dto, Authentication auth) {
    UUID doctorId = UUID.fromString(auth.getName());
    if (dto.getDoctor() == null) {
      dto.setDoctor(new DoctorDTO());
    }
    dto.getDoctor().setUuid(doctorId);
    return new ResponseEntity<>(scheduleService.create(dto), HttpStatus.CREATED);
  }

  @PutMapping("/my-schedules/{id}/status")
  public ResponseEntity<ScheduleDTO> updateMyScheduleStatus(
      @PathVariable Long id, 
      @RequestBody ScheduleDTO dto, 
      org.springframework.security.core.Authentication auth) {
      UUID doctorId = UUID.fromString(auth.getName());
      return ResponseEntity.ok(scheduleService.updateMyScheduleStatus(id, dto.getStatus(), doctorId));
  }

  @PostMapping("/generate")
  public ResponseEntity<List<ScheduleDTO>> generateSchedules(
      @Valid @RequestBody GenerateSchedulesBody body) {
    return new ResponseEntity<>(scheduleService.generateSchedules(body), HttpStatus.CREATED);
  }

  // Template-driven counterpart of /generate above: reads the applicable
  // ScheduleTemplate(s) for the requested period instead of the caller
  // re-typing start/end/interval. /generate is UNCHANGED — Angular's
  // specialty-detail screen keeps calling it exactly as before.
  @PostMapping("/generate-from-template")
  public ResponseEntity<List<ScheduleDTO>> generateSchedulesFromTemplates(
      @Valid @RequestBody GenerateSchedulesFromTemplateBody body) {
    return new ResponseEntity<>(scheduleService.generateSchedulesFromTemplates(body), HttpStatus.CREATED);
  }
}
