package com.devluis.controller;

import com.devluis.dto.CoveragePlanDTO;
import com.devluis.services.CoveragePlanService;

import jakarta.validation.Valid;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coverage-plans")
@Data
public class CoveragePlanController {

  private final CoveragePlanService coveragePlanService;

  @PostMapping("/save")
  public ResponseEntity<CoveragePlanDTO> create(@Valid @RequestBody CoveragePlanDTO dto) {
    return new ResponseEntity<>(coveragePlanService.create(dto), HttpStatus.CREATED);
  }

  @GetMapping
  public Page<CoveragePlanDTO> getAll(
      @RequestParam(required = false) Long insurerId,
      @PageableDefault(size = 10) Pageable pageable) {
    return coveragePlanService.getAll(insurerId, pageable);
  }

  @GetMapping("/{id}")
  public ResponseEntity<CoveragePlanDTO> getById(@PathVariable Long id) {
    return ResponseEntity.ok(coveragePlanService.getById(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<CoveragePlanDTO> update(@PathVariable Long id, @Valid @RequestBody CoveragePlanDTO dto) {
    return ResponseEntity.ok(coveragePlanService.update(id, dto));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    coveragePlanService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
