package com.devluis.controller;

import com.devluis.dto.ServicePackageDTO;
import com.devluis.services.ServicePackageService;

import jakarta.validation.Valid;
import lombok.Data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// "precios/paquetes" admin destination. GET (bare list) is public — see
// GlobalConfig: a prospective patient may legitimately want to see bundled
// offers before logging in, same reasoning as GET /api/services. Writes are
// ROLE_ADMIN only, same catalogue-writes tier as every other admin-managed
// resource in this codebase.
@RestController
@RequestMapping("/api/packages")
@Data
public class ServicePackageController {

  private final ServicePackageService servicePackageService;

  @PostMapping("/save")
  public ResponseEntity<ServicePackageDTO> create(@Valid @RequestBody ServicePackageDTO dto) {
    return new ResponseEntity<>(servicePackageService.create(dto), HttpStatus.CREATED);
  }

  @GetMapping
  public Page<ServicePackageDTO> getAll(
      @RequestParam(required = false) String name,
      @PageableDefault(size = 10) Pageable pageable) {
    return servicePackageService.getAll(name, pageable);
  }

  @GetMapping("/{id}")
  public ResponseEntity<ServicePackageDTO> getById(@PathVariable Long id) {
    return ResponseEntity.ok(servicePackageService.getById(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ServicePackageDTO> update(@PathVariable Long id, @Valid @RequestBody ServicePackageDTO dto) {
    return ResponseEntity.ok(servicePackageService.update(id, dto));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    servicePackageService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
