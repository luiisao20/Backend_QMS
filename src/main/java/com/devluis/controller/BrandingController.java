package com.devluis.controller;

import com.devluis.dto.BrandingDTO;
import com.devluis.services.BrandingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// GET is public on purpose (see GlobalConfig: permitAll()) — branding is by
// definition public identity data a landing page needs before the visitor
// ever authenticates. PUT is ROLE_ADMIN. There is no POST/DELETE here: see
// Branding's own docblock for why this is a singleton with only a read and
// an upsert.
@RestController
@RequestMapping("/api/branding")
@RequiredArgsConstructor
public class BrandingController {

  private final BrandingService brandingService;

  @GetMapping
  public ResponseEntity<BrandingDTO> get() {
    return ResponseEntity.ok(brandingService.get());
  }

  @PutMapping
  public ResponseEntity<BrandingDTO> save(@Valid @RequestBody BrandingDTO dto) {
    return ResponseEntity.ok(brandingService.save(dto));
  }
}
