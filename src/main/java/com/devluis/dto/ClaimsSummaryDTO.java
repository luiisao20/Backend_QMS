package com.devluis.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for GET /api/accounting/claims-summary — the "finanzas/reclamos"
 * reporting slice of "finanzas/contabilidad". Bounded by {@code submittedAt}
 * within {@code [from, to]}, grouped by {@link com.devluis.types.ClaimStatus}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClaimsSummaryDTO {
  private LocalDate from;
  private LocalDate to;
  private List<ClaimStatusSummaryRow> claimsByStatus;
}
