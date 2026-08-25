package com.devluis.controller;

import com.devluis.dto.SessionPlanDTO;
import com.devluis.services.SessionPlanService;

import jakarta.validation.Valid;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// "precios/sesiones" admin destination. GET (bare list) is public — see
// GlobalConfig: a prospective patient may legitimately want to see
// available session bundles (physiotherapy, cleanings) before logging in,
// same reasoning as GET /api/services. Writes are ROLE_ADMIN only, same
// catalogue-writes tier as every other admin-managed resource here.
@RestController
@RequestMapping("/api/session-plans")
@Data
public class SessionPlanController {

  private final SessionPlanService sessionPlanService;

  @PostMapping("/save")
  public ResponseEntity<SessionPlanDTO> create(@Valid @RequestBody SessionPlanDTO dto) {
    return new ResponseEntity<>(sessionPlanService.create(dto), HttpStatus.CREATED);
  }

  @GetMapping
  public Page<SessionPlanDTO> getAll(
      @RequestParam(required = false) Long servicioId,
      @PageableDefault(size = 10) Pageable pageable) {
    return sessionPlanService.getAll(servicioId, pageable);
  }

  @GetMapping("/{id}")
  public ResponseEntity<SessionPlanDTO> getById(@PathVariable Long id) {
    return ResponseEntity.ok(sessionPlanService.getById(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<SessionPlanDTO> update(@PathVariable Long id, @Valid @RequestBody SessionPlanDTO dto) {
    return ResponseEntity.ok(sessionPlanService.update(id, dto));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    sessionPlanService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
