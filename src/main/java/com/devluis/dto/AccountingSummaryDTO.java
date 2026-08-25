package com.devluis.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for GET /api/accounting/summary — the "finanzas/contabilidad"
 * screen. A REPORTING view over Invoice/Payment, not a fourth entity (see
 * AccountingService).
 *
 * <p>Two different kinds of numbers on purpose, clearly separated:
 * <ul>
 *   <li>PERIOD-bounded ({@code [from, to]}): {@code invoicedByStatus}
 *       (invoices ISSUED in the period, grouped by their CURRENT status) and
 *       {@code collectedByMethod} (payments RECEIVED in the period, grouped
 *       by method). Both answer "what happened between these two dates".</li>
 *   <li>{@code outstandingNow}: NOT period-bounded — the sum of
 *       {@code (total - sum(payments))} across every non-VOID invoice that
 *       exists today, regardless of when it was issued. "Outstanding as of
 *       last month" would require replaying the full payment timeline up to
 *       that date, which no requirement asked for and this system does not
 *       model — so this figure is deliberately always "right now", never a
 *       historical snapshot.</li>
 * </ul>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AccountingSummaryDTO {
  private LocalDate from;
  private LocalDate to;
  private List<InvoiceStatusSummaryRow> invoicedByStatus;
  private List<PaymentMethodSummaryRow> collectedByMethod;
  private BigDecimal outstandingNow;
}
