package com.devluis.controller;

import com.devluis.dto.StablishmentDTO;
import com.devluis.services.StablishmentService;
import jakarta.validation.Valid;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stablishments")
@Data
public class StablishmentController {

  private final StablishmentService stablishmentService;

  @PostMapping("/save")
  public ResponseEntity<StablishmentDTO> create(@Valid @RequestBody StablishmentDTO dto) {
    return new ResponseEntity<>(stablishmentService.create(dto), HttpStatus.CREATED);
  }

  @GetMapping
  public Page<StablishmentDTO> getAll(
      @PageableDefault(size = 10) Pageable pageable) {
    return stablishmentService.getAll(pageable);
  }

  @GetMapping("/{id}")
  public ResponseEntity<StablishmentDTO> getById(@PathVariable Long id) {
    return ResponseEntity.ok(stablishmentService.getById(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<StablishmentDTO> update(@PathVariable Long id, @Valid @RequestBody StablishmentDTO dto) {
    return ResponseEntity.ok(stablishmentService.update(id, dto));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    stablishmentService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/services/{serviceId}")
  public ResponseEntity<StablishmentDTO> assignService(@PathVariable Long id, @PathVariable Long serviceId) {
    return ResponseEntity.ok(stablishmentService.assignService(id, serviceId));
  }
}
