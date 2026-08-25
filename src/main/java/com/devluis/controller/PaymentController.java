package com.devluis.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.devluis.dto.PaymentDTO;
import com.devluis.services.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Nested entirely under "/api/invoices/{invoiceId}/payments" — a Payment
 * never exists independent of its Invoice, so there is no bare
 * "/api/payments" root at all. Staff-only throughout (ROLE_EMPLOYEE or
 * ROLE_ADMIN): a patient's own payment history is already visible embedded
 * in {@code InvoiceDTO.payments} via GET /api/invoices/me — this controller
 * is the front-desk "register/inspect a receipt" surface, not a patient
 * self-service one.
 */
@RestController
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;

  @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYEE', 'ROLE_ADMIN')")
  @PostMapping("/api/invoices/{invoiceId}/payments")
  public ResponseEntity<PaymentDTO> create(
      @PathVariable Long invoiceId, @Valid @RequestBody PaymentDTO dto, Authentication auth) {
    return new ResponseEntity<>(paymentService.create(invoiceId, dto, auth), HttpStatus.CREATED);
  }

  @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYEE', 'ROLE_ADMIN')")
  @GetMapping("/api/invoices/{invoiceId}/payments")
  public ResponseEntity<List<PaymentDTO>> getForInvoice(@PathVariable Long invoiceId) {
    return ResponseEntity.ok(paymentService.getForInvoice(invoiceId));
  }
}
