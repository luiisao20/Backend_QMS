package com.devluis.controller;

import com.devluis.dto.InsurerDTO;
import com.devluis.services.InsurerService;

import jakarta.validation.Valid;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/insurers")
@Data
public class InsurerController {

  private final InsurerService insurerService;

  @PostMapping("/save")
  public ResponseEntity<InsurerDTO> create(@Valid @RequestBody InsurerDTO dto) {
    return new ResponseEntity<>(insurerService.create(dto), HttpStatus.CREATED);
  }

  @GetMapping
  public Page<InsurerDTO> getAll(
      @RequestParam(required = false) String name,
      @PageableDefault(size = 10) Pageable pageable) {
    return insurerService.getAll(name, pageable);
  }

  @GetMapping("/{id}")
  public ResponseEntity<InsurerDTO> getById(@PathVariable Long id) {
    return ResponseEntity.ok(insurerService.getById(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<InsurerDTO> update(@PathVariable Long id, @Valid @RequestBody InsurerDTO dto) {
    return ResponseEntity.ok(insurerService.update(id, dto));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    insurerService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
