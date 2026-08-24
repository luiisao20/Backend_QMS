package com.devluis.controller;

import com.devluis.dto.BlockReasonDTO;
import com.devluis.services.BlockReasonService;
import com.devluis.types.BlockReasonKind;
import jakarta.validation.Valid;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Catálogo de motivos de bloqueo — la pantalla Bloqueo de citas → Motivos.
 *
 * `/active?kind=` es lo que hace que Feriados, Vacaciones y Permisos puedan
 * ofrecer solo los motivos que les corresponden en vez de la lista entera.
 */
@RestController
@RequestMapping("/api/block-reasons")
@Data
public class BlockReasonController {

  private final BlockReasonService blockReasonService;

  @PostMapping
  public ResponseEntity<BlockReasonDTO> create(@Valid @RequestBody BlockReasonDTO dto) {
    return new ResponseEntity<>(blockReasonService.create(dto), HttpStatus.CREATED);
  }

  @GetMapping
  public Page<BlockReasonDTO> getAll(
      @PageableDefault(size = 10) Pageable pageable) {
    return blockReasonService.getAll(pageable);
  }

  @GetMapping("/active")
  public ResponseEntity<List<BlockReasonDTO>> getActive(
      @RequestParam(required = false) BlockReasonKind kind) {
    return ResponseEntity.ok(blockReasonService.getActive(kind));
  }

  @GetMapping("/{id}")
  public ResponseEntity<BlockReasonDTO> getById(@PathVariable Long id) {
    return ResponseEntity.ok(blockReasonService.getById(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<BlockReasonDTO> update(@PathVariable Long id, @Valid @RequestBody BlockReasonDTO dto) {
    return ResponseEntity.ok(blockReasonService.update(id, dto));
  }

  /** Desactiva, no borra: hay ausencias apuntando a este motivo. */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    blockReasonService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
