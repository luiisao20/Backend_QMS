package com.devluis.controller;

import java.util.List;

import com.devluis.dto.AdminModuleDTO;
import com.devluis.dto.ModuleToggleBody;
import com.devluis.services.AdminModuleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Both GET and PUT are ROLE_ADMIN-only (see GlobalConfig) — see the apply
// report for why the read tier is admin-only too (nothing outside the
// admin panel has a legitimate use for this yet, unlike Branding).
//
// No pagination: this is a small, fixed catalog (12 rows, see
// AdminModuleService.SEED), not an open-ended admin-created list — same
// reasoning as why there is no POST/DELETE here at all.
@RestController
@RequestMapping("/api/admin-modules")
@RequiredArgsConstructor
public class AdminModuleController {

  private final AdminModuleService adminModuleService;

  @GetMapping
  public List<AdminModuleDTO> getAll() {
    return adminModuleService.getAll();
  }

  @PutMapping("/{moduleKey}")
  public ResponseEntity<AdminModuleDTO> setEnabled(
      @PathVariable String moduleKey, @Valid @RequestBody ModuleToggleBody body) {
    return ResponseEntity.ok(adminModuleService.setEnabled(moduleKey, body.getEnabled()));
  }
}
