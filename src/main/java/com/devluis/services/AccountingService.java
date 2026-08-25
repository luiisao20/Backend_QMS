package com.devluis.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;

import com.devluis.dto.AccountingSummaryDTO;
import com.devluis.dto.ClaimsSummaryDTO;
import com.devluis.repository.ClaimRepository;
import com.devluis.repository.InvoiceRepository;
import com.devluis.repository.PaymentRepository;

import lombok.Data;

/**
 * finanzas/contabilidad: a READ-ONLY reporting view over Invoice/Payment/
 * Claim — NOT a fourth entity (see the apply report). Every figure here is
 * computed by a repository-level GROUP BY/SUM/COUNT query; this class only
 * reshapes those already-small, already-aggregated result sets — same
 * "aggregate in the database, never by looping over rows in Java" precedent
 * as {@link MetricsService}.
 *
 * <p>See {@link AccountingSummaryDTO}'s own docblock for why
 * {@code outstandingNow} is deliberately NOT bound by {@code [from, to]}
 * while {@code invoicedByStatus}/{@code collectedByMethod} are.
 */
@Service
@Data
public class AccountingService {
  private static final int DEFAULT_RANGE_DAYS = 30;

  private final InvoiceRepository invoiceRepository;
  private final PaymentRepository paymentRepository;
  private final ClaimRepository claimRepository;

  public AccountingSummaryDTO getSummary(LocalDate from, LocalDate to) {
    DateRange range = resolveRange(from, to);
    OffsetDateTime fromInclusive = toOffsetStart(range.from());
    OffsetDateTime toExclusive = toOffsetStart(range.to().plusDays(1));

    BigDecimal outstandingNow = invoiceRepository.sumTotalForNonVoidInvoices()
        .subtract(paymentRepository.sumAmountForNonVoidInvoices());

    return AccountingSummaryDTO.builder()
        .from(range.from())
        .to(range.to())
        .invoicedByStatus(invoiceRepository.countAndSumByStatusInRange(fromInclusive, toExclusive))
        .collectedByMethod(paymentRepository.countAndSumByMethodInRange(fromInclusive, toExclusive))
        .outstandingNow(outstandingNow)
        .build();
  }

  public ClaimsSummaryDTO getClaimsSummary(LocalDate from, LocalDate to) {
    DateRange range = resolveRange(from, to);
    OffsetDateTime fromInclusive = toOffsetStart(range.from());
    OffsetDateTime toExclusive = toOffsetStart(range.to().plusDays(1));

    return ClaimsSummaryDTO.builder()
        .from(range.from())
        .to(range.to())
        .claimsByStatus(claimRepository.countAndSumByStatusInRange(fromInclusive, toExclusive))
        .build();
  }

  // Same "absent `to` defaults to today, absent `from` defaults to
  // DEFAULT_RANGE_DAYS before `to`" idiom as MetricsService#resolveRange.
  private DateRange resolveRange(LocalDate from, LocalDate to) {
    LocalDate resolvedTo = to != null ? to : LocalDate.now();
    LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(DEFAULT_RANGE_DAYS - 1L);

    if (resolvedFrom.isAfter(resolvedTo)) {
      throw new RuntimeException("La fecha 'desde' no puede ser posterior a la fecha 'hasta'");
    }

    return new DateRange(resolvedFrom, resolvedTo);
  }

  private OffsetDateTime toOffsetStart(LocalDate date) {
    return date.atStartOfDay().atOffset(ZoneOffset.UTC);
  }

  private record DateRange(LocalDate from, LocalDate to) {
  }
}
