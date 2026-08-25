package com.devluis.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.devluis.dto.AccountingSummaryDTO;
import com.devluis.dto.ClaimsSummaryDTO;
import com.devluis.services.AccountingService;

import lombok.RequiredArgsConstructor;

/**
 * "finanzas/contabilidad". Read-only; every number is computed by
 * {@link AccountingService} from Invoice/Payment/Claim — no new table (same
 * "a reporting view, not a fourth entity" precedent as
 * {@code MetricsController}). No try/catch here either, same reasoning:
 * business-rule failures (an invalid date range) are plain
 * RuntimeExceptions, uniformly handled by
 * {@link com.devluis.exception.GlobalExceptionHandler}.
 *
 * <p>Authorization: ROLE_EMPLOYEE or ROLE_ADMIN only — same billing tier as
 * the rest of the finance group. Deliberately narrower than
 * {@code /api/metrics/**} (which also allows ROLE_DOCTOR): money/collections
 * reporting has no clinical need-to-know, same reasoning
 * {@code PatientCoverageAccessGuard}/{@code InvoiceAccessGuard} already
 * apply. One uniform tier for every route here, so gating is entirely at the
 * URL-matcher level in GlobalConfig — no per-method {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/accounting")
@RequiredArgsConstructor
public class AccountingController {

  private final AccountingService accountingService;

  @GetMapping("/summary")
  public AccountingSummaryDTO getSummary(
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
    return accountingService.getSummary(from, to);
  }

  @GetMapping("/claims-summary")
  public ClaimsSummaryDTO getClaimsSummary(
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
    return accountingService.getClaimsSummary(from, to);
  }
}
