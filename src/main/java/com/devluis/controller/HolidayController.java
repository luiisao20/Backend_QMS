package com.devluis.controller;

import com.devluis.dto.HolidayDTO;
import com.devluis.services.HolidayService;

import jakarta.validation.Valid;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/holidays")
@Data
public class HolidayController {

  private final HolidayService holidayService;

  @PostMapping("/save")
  public ResponseEntity<HolidayDTO> create(@Valid @RequestBody HolidayDTO dto) {
    return new ResponseEntity<>(holidayService.create(dto), HttpStatus.CREATED);
  }

  @GetMapping
  public Page<HolidayDTO> getAll(
      @RequestParam(required = false) Long stablishmentId,
      @PageableDefault(size = 10) Pageable pageable) {
    return holidayService.getAll(stablishmentId, pageable);
  }

  @GetMapping("/{id}")
  public ResponseEntity<HolidayDTO> getById(@PathVariable Long id) {
    return ResponseEntity.ok(holidayService.getById(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<HolidayDTO> update(@PathVariable Long id, @Valid @RequestBody HolidayDTO dto) {
    return ResponseEntity.ok(holidayService.update(id, dto));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    holidayService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
