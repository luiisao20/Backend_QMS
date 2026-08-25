package com.devluis.dto;

import java.math.BigDecimal;

import com.devluis.types.ClaimStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal aggregation row: how many claims, and how much amountClaimed,
 * exist for a single status within a date range (bounded by submittedAt).
 * Not part of the public API — only used as a JPQL constructor-expression
 * target (see ClaimRepository) and reshaped by AccountingService.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClaimStatusSummaryRow {
  private ClaimStatus status;
  private Long count;
  private BigDecimal totalAmount;
}
