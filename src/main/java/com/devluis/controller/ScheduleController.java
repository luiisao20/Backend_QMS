package com.devluis.controller;

import com.devluis.dto.ScheduleDTO;
import com.devluis.services.ScheduleService;
import jakarta.validation.Valid;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/schedules")
@Data
public class ScheduleController {

  private final ScheduleService scheduleService;

  @PostMapping
  public ResponseEntity<ScheduleDTO> create(@Valid @RequestBody ScheduleDTO dto) {
    return new ResponseEntity<>(scheduleService.create(dto), HttpStatus.CREATED);
  }

  @GetMapping
  public Page<ScheduleDTO> getAll(
      @PageableDefault(size = 10) Pageable pageable) {
    return scheduleService.getAll(pageable);
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
}
