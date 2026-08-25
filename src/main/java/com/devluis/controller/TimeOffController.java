package com.devluis.controller;

import java.util.UUID;

import com.devluis.dto.TimeOffDTO;
import com.devluis.services.TimeOffService;
import com.devluis.types.TimeOffKind;

import jakarta.validation.Valid;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/time-offs")
@Data
public class TimeOffController {

  private final TimeOffService timeOffService;

  @PostMapping("/save")
  public ResponseEntity<TimeOffDTO> create(@Valid @RequestBody TimeOffDTO dto) {
    return new ResponseEntity<>(timeOffService.create(dto), HttpStatus.CREATED);
  }

  @GetMapping
  public Page<TimeOffDTO> getAll(
      @RequestParam(required = false) UUID doctorId,
      @RequestParam(required = false) TimeOffKind kind,
      @PageableDefault(size = 10) Pageable pageable) {
    return timeOffService.getAll(doctorId, kind, pageable);
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
