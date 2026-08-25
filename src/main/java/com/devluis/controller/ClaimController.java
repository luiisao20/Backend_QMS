package com.devluis.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.devluis.dto.ClaimDTO;
import com.devluis.dto.RejectClaimBody;
import com.devluis.services.ClaimService;
import com.devluis.types.ClaimStatus;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * "finanzas/reclamos". Every route here is staff-only (ROLE_EMPLOYEE or
 * ROLE_ADMIN) with NO exception — unlike Invoice, there is no patient-facing
 * "/me" for claims (see the apply report: a patient's invoice already shows
 * what they owe; who the clinic is chasing at the insurer is an internal
 * billing-operations concern). Because every method shares the exact same
 * tier, gating is entirely at the URL-matcher level in GlobalConfig — no
 * per-method {@code @PreAuthorize} here, same "one uniform tier" precedent
 * as {@code MetricsController}.
 */
@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
public class ClaimController {

  private final ClaimService claimService;

  @PostMapping
  public ResponseEntity<ClaimDTO> create(@Valid @RequestBody ClaimDTO dto) {
    return new ResponseEntity<>(claimService.create(dto.getInvoiceId()), HttpStatus.CREATED);
  }

  @GetMapping
  public ResponseEntity<Page<ClaimDTO>> search(
      @RequestParam(required = false) Long invoiceId,
      @RequestParam(required = false) ClaimStatus status,
      @PageableDefault(size = 10) Pageable pageable) {
    return ResponseEntity.ok(claimService.search(invoiceId, status, pageable));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ClaimDTO> getById(@PathVariable Long id) {
    return ResponseEntity.ok(claimService.getById(id));
  }

  @PutMapping("/{id}/accept")
  public ResponseEntity<ClaimDTO> accept(@PathVariable Long id) {
    return ResponseEntity.ok(claimService.accept(id));
  }

  @PutMapping("/{id}/reject")
  public ResponseEntity<ClaimDTO> reject(@PathVariable Long id, @Valid @RequestBody RejectClaimBody body) {
    return ResponseEntity.ok(claimService.reject(id, body.getReason()));
  }

  @PutMapping("/{id}/mark-paid")
  public ResponseEntity<ClaimDTO> markAsPaid(@PathVariable Long id, Authentication auth) {
    UUID receivedByUuid = UUID.fromString(auth.getName());
    return ResponseEntity.ok(claimService.markAsPaid(id, receivedByUuid));
  }
}
