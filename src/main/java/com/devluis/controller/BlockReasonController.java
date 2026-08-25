package com.devluis.controller;

import com.devluis.dto.BlockReasonDTO;
import com.devluis.services.BlockReasonService;

import jakarta.validation.Valid;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/block-reasons")
@Data
public class BlockReasonController {

  private final BlockReasonService blockReasonService;

  @PostMapping("/save")
  public ResponseEntity<BlockReasonDTO> create(@Valid @RequestBody BlockReasonDTO dto) {
    return new ResponseEntity<>(blockReasonService.create(dto), HttpStatus.CREATED);
  }

  @GetMapping
  public Page<BlockReasonDTO> getAll(
      @RequestParam(required = false) String description,
      @PageableDefault(size = 10) Pageable pageable) {
    return blockReasonService.getAll(description, pageable);
  }

  @GetMapping("/{id}")
  public ResponseEntity<BlockReasonDTO> getById(@PathVariable Long id) {
    return ResponseEntity.ok(blockReasonService.getById(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<BlockReasonDTO> update(@PathVariable Long id, @Valid @RequestBody BlockReasonDTO dto) {
    return ResponseEntity.ok(blockReasonService.update(id, dto));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    blockReasonService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
