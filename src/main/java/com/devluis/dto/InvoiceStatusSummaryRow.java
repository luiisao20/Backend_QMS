package com.devluis.dto;

import java.math.BigDecimal;

import com.devluis.types.InvoiceStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal aggregation row: how many invoices, and how much total value,
 * exist for a single status within a date range. Not part of the public
 * API — only used as a JPQL constructor-expression target (see
 * InvoiceRepository) and reshaped by AccountingService.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InvoiceStatusSummaryRow {
  private InvoiceStatus status;
  private Long count;
  private BigDecimal totalAmount;
}
