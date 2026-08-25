package com.devluis.dto;

import java.math.BigDecimal;

import com.devluis.types.PaymentMethod;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal aggregation row: how many payments, and how much money, were
 * collected through a single method within a date range. Not part of the
 * public API — only used as a JPQL constructor-expression target (see
 * PaymentRepository) and reshaped by AccountingService.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentMethodSummaryRow {
  private PaymentMethod method;
  private Long count;
  private BigDecimal totalAmount;
}
