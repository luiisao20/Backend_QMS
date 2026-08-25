package com.devluis.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devluis.dto.InvoiceDTO;
import com.devluis.dto.VoidInvoiceBody;
import com.devluis.services.InvoiceService;
import com.devluis.types.InvoiceStatus;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * No class-level {@code @RequestMapping}: routes span two resource roots
 * ("/api/invoices/**" and the staff sub-resource
 * "/api/patients/{patientId}/invoices"), same two-root shape as
 * {@code EncounterController}/{@code PatientCoverageController}.
 *
 * <p>Coarse gating: create/search/the nested per-patient browse require
 * ROLE_EMPLOYEE or ROLE_ADMIN — billing is front-desk work in this codebase,
 * not clinical (see {@link com.devluis.services.InvoiceAccessGuard}).
 * Voiding is ROLE_ADMIN ONLY — stricter than ordinary billing writes,
 * because it corrects/reverses a financial record rather than creating one.
 * "/me" and "/{id}" are deliberately NOT gated here at the URL level (no
 * explicit matcher in GlobalConfig either) — exactly like
 * {@code PatientCoverageController}'s own "/me"/"/{id}": any authenticated
 * role may reach them, and {@link InvoiceService} enforces per-record
 * ownership via {@link com.devluis.services.InvoiceAccessGuard}.
 */
@RestController
@RequiredArgsConstructor
public class InvoiceController {

  private final InvoiceService invoiceService;

  @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYEE', 'ROLE_ADMIN', 'ROLE_DOCTOR')")
  @PostMapping("/api/invoices")
  public ResponseEntity<InvoiceDTO> create(@Valid @RequestBody InvoiceDTO dto, Authentication auth) {
    return new ResponseEntity<>(invoiceService.create(dto, auth), HttpStatus.CREATED);
  }

  // The "finanzas/facturacion" patient self-service screen. Same "/me"
  // idiom as GET /api/turns/me, GET /api/encounters/me, GET
  // /api/patient-coverages/me.
  @GetMapping("/api/invoices/me")
  public ResponseEntity<Page<InvoiceDTO>> getMyInvoices(
      @PageableDefault(size = 10) Pageable pageable, Authentication auth) {
    UUID patientUuid = UUID.fromString(auth.getName());
    return ResponseEntity.ok(invoiceService.getForPatient(patientUuid, pageable));
  }

  @GetMapping("/api/invoices/{id}")
  public ResponseEntity<InvoiceDTO> getById(@PathVariable Long id, Authentication auth) {
    return ResponseEntity.ok(invoiceService.getById(id, auth));
  }

  // Staff-wide browse/search — e.g. an admin billing queue filtered by
  // status, optionally scoped to one patient by uuid.
  @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYEE', 'ROLE_ADMIN')")
  @GetMapping("/api/invoices")
  public ResponseEntity<Page<InvoiceDTO>> search(
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) InvoiceStatus status,
      @PageableDefault(size = 10) Pageable pageable) {
    return ResponseEntity.ok(invoiceService.search(patientId, status, pageable));
  }

  // The staff "ver facturas de este paciente" screen, reached from Patient
  // Detail — same nested-resource idiom as
  // GET /api/patients/{patientId}/encounters.
  @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYEE', 'ROLE_ADMIN')")
  @GetMapping("/api/patients/{patientId}/invoices")
  public ResponseEntity<Page<InvoiceDTO>> getForPatient(
      @PathVariable UUID patientId, @PageableDefault(size = 10) Pageable pageable) {
    return ResponseEntity.ok(invoiceService.getForPatient(patientId, pageable));
  }

  @PreAuthorize("hasAuthority('ROLE_ADMIN')")
  @PutMapping("/api/invoices/{id}/void")
  public ResponseEntity<InvoiceDTO> voidInvoice(
      @PathVariable Long id, @Valid @RequestBody VoidInvoiceBody body, Authentication auth) {
    UUID voidedByUuid = UUID.fromString(auth.getName());
    return ResponseEntity.ok(invoiceService.voidInvoice(id, body.getReason(), voidedByUuid));
  }

  // No @DeleteMapping anywhere on this controller: an Invoice is a financial
  // record and is never hard-deletable through this API — VOID is the only
  // "removal" mechanism. See Invoice's own docblock.
}
