package com.devluis.controller;

import com.devluis.dto.PromotionDTO;
import com.devluis.services.PromotionService;

import jakarta.validation.Valid;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// "precios/promociones" admin destination. GET (bare list) is public — see
// GlobalConfig: a prospective patient may legitimately want to see current
// promotions before logging in, same reasoning as GET /api/services.
// Writes are ROLE_ADMIN only, same catalogue-writes tier as every other
// admin-managed resource in this codebase.
@RestController
@RequestMapping("/api/promotions")
@Data
public class PromotionController {

  private final PromotionService promotionService;

  @PostMapping("/save")
  public ResponseEntity<PromotionDTO> create(@Valid @RequestBody PromotionDTO dto) {
    return new ResponseEntity<>(promotionService.create(dto), HttpStatus.CREATED);
  }

  @GetMapping
  public Page<PromotionDTO> getAll(
      @RequestParam(required = false) Long servicioId,
      @PageableDefault(size = 10) Pageable pageable) {
    return promotionService.getAll(servicioId, pageable);
  }

  @GetMapping("/{id}")
  public ResponseEntity<PromotionDTO> getById(@PathVariable Long id) {
    return ResponseEntity.ok(promotionService.getById(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<PromotionDTO> update(@PathVariable Long id, @Valid @RequestBody PromotionDTO dto) {
    return ResponseEntity.ok(promotionService.update(id, dto));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    promotionService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
