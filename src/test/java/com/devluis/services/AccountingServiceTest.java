package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.devluis.dto.AccountingSummaryDTO;
import com.devluis.dto.ClaimStatusSummaryRow;
import com.devluis.dto.ClaimsSummaryDTO;
import com.devluis.dto.InvoiceStatusSummaryRow;
import com.devluis.dto.PaymentMethodSummaryRow;
import com.devluis.repository.ClaimRepository;
import com.devluis.repository.InvoiceRepository;
import com.devluis.repository.PaymentRepository;
import com.devluis.types.ClaimStatus;
import com.devluis.types.InvoiceStatus;
import com.devluis.types.PaymentMethod;

/**
 * finanzas/contabilidad — a REPORTING view, not a fourth entity (see
 * AccountingService's docblock). Every number here comes from a repository
 * aggregate (GROUP BY/SUM/COUNT); this service only reshapes already-small
 * result sets, same "never loop over raw rows in Java" precedent as
 * MetricsService.
 */
@ExtendWith(MockitoExtension.class)
class AccountingServiceTest {

  @Mock
  private InvoiceRepository invoiceRepository;
  @Mock
  private PaymentRepository paymentRepository;
  @Mock
  private ClaimRepository claimRepository;

  private AccountingService accountingService;

  @BeforeEach
  void setUp() {
    accountingService = new AccountingService(invoiceRepository, paymentRepository, claimRepository);
  }

  @Test
  void getSummary_returnsInvoicedByStatus_andCollectedByMethod_forTheGivenRange() {
    LocalDate from = LocalDate.of(2026, 8, 1);
    LocalDate to = LocalDate.of(2026, 8, 10);
    when(invoiceRepository.countAndSumByStatusInRange(any(), any())).thenReturn(List.of(
        InvoiceStatusSummaryRow.builder().status(InvoiceStatus.PAID).count(5L).totalAmount(new BigDecimal("500.00")).build(),
        InvoiceStatusSummaryRow.builder().status(InvoiceStatus.ISSUED).count(2L).totalAmount(new BigDecimal("80.00")).build()));
    when(paymentRepository.countAndSumByMethodInRange(any(), any())).thenReturn(List.of(
        PaymentMethodSummaryRow.builder().method(PaymentMethod.CASH).count(4L).totalAmount(new BigDecimal("300.00")).build()));
    when(invoiceRepository.sumTotalForNonVoidInvoices()).thenReturn(new BigDecimal("580.00"));
    when(paymentRepository.sumAmountForNonVoidInvoices()).thenReturn(new BigDecimal("300.00"));

    AccountingSummaryDTO dto = accountingService.getSummary(from, to);

    assertThat(dto.getFrom()).isEqualTo(from);
    assertThat(dto.getTo()).isEqualTo(to);
    assertThat(dto.getInvoicedByStatus()).hasSize(2);
    assertThat(dto.getInvoicedByStatus().get(0).getTotalAmount()).isEqualByComparingTo("500.00");
    assertThat(dto.getCollectedByMethod()).hasSize(1);
    assertThat(dto.getCollectedByMethod().get(0).getTotalAmount()).isEqualByComparingTo("300.00");
  }

  @Test
  void getSummary_outstandingNow_isTotalInvoicedMinusTotalCollected_forNonVoidInvoices_regardlessOfTheRange() {
    when(invoiceRepository.countAndSumByStatusInRange(any(), any())).thenReturn(List.of());
    when(paymentRepository.countAndSumByMethodInRange(any(), any())).thenReturn(List.of());
    when(invoiceRepository.sumTotalForNonVoidInvoices()).thenReturn(new BigDecimal("1000.00"));
    when(paymentRepository.sumAmountForNonVoidInvoices()).thenReturn(new BigDecimal("650.00"));

    AccountingSummaryDTO dto = accountingService.getSummary(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31));

    assertThat(dto.getOutstandingNow()).isEqualByComparingTo("350.00");
  }

  @Test
  void getSummary_defaultsToTheLast30Days_whenNoRangeIsGiven() {
    when(invoiceRepository.countAndSumByStatusInRange(any(), any())).thenReturn(List.of());
    when(paymentRepository.countAndSumByMethodInRange(any(), any())).thenReturn(List.of());
    when(invoiceRepository.sumTotalForNonVoidInvoices()).thenReturn(BigDecimal.ZERO.setScale(2));
    when(paymentRepository.sumAmountForNonVoidInvoices()).thenReturn(BigDecimal.ZERO.setScale(2));

    AccountingSummaryDTO dto = accountingService.getSummary(null, null);

    assertThat(dto.getTo()).isEqualTo(LocalDate.now());
    assertThat(dto.getFrom()).isEqualTo(LocalDate.now().minusDays(29));
  }

  @Test
  void getSummary_throws_whenFromIsAfterTo() {
    LocalDate from = LocalDate.of(2026, 8, 10);
    LocalDate to = LocalDate.of(2026, 8, 1);

    assertThatThrownBy(() -> accountingService.getSummary(from, to))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("posterior");
  }

  @Test
  void getSummary_convertsTheLocalDateRangeToHalfOpenOffsetDateTimeBoundaries_forInvoicedByStatus() {
    LocalDate from = LocalDate.of(2026, 8, 1);
    LocalDate to = LocalDate.of(2026, 8, 10);
    when(invoiceRepository.countAndSumByStatusInRange(any(), any())).thenReturn(List.of());
    when(paymentRepository.countAndSumByMethodInRange(any(), any())).thenReturn(List.of());
    when(invoiceRepository.sumTotalForNonVoidInvoices()).thenReturn(BigDecimal.ZERO.setScale(2));
    when(paymentRepository.sumAmountForNonVoidInvoices()).thenReturn(BigDecimal.ZERO.setScale(2));

    accountingService.getSummary(from, to);

    OffsetDateTime expectedFrom = from.atStartOfDay().atOffset(ZoneOffset.UTC);
    OffsetDateTime expectedTo = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
    verify(invoiceRepository).countAndSumByStatusInRange(expectedFrom, expectedTo);
    verify(paymentRepository).countAndSumByMethodInRange(expectedFrom, expectedTo);
  }

  @Test
  void getClaimsSummary_returnsClaimsGroupedByStatus_forTheGivenRange() {
    LocalDate from = LocalDate.of(2026, 8, 1);
    LocalDate to = LocalDate.of(2026, 8, 10);
    when(claimRepository.countAndSumByStatusInRange(any(), any())).thenReturn(List.of(
        ClaimStatusSummaryRow.builder().status(ClaimStatus.PAID).count(3L).totalAmount(new BigDecimal("240.00")).build(),
        ClaimStatusSummaryRow.builder().status(ClaimStatus.REJECTED).count(1L).totalAmount(new BigDecimal("80.00")).build()));

    ClaimsSummaryDTO dto = accountingService.getClaimsSummary(from, to);

    assertThat(dto.getClaimsByStatus()).hasSize(2);
    assertThat(dto.getClaimsByStatus().get(0).getStatus()).isEqualTo(ClaimStatus.PAID);
    assertThat(dto.getClaimsByStatus().get(1).getTotalAmount()).isEqualByComparingTo("80.00");
  }

  @Test
  void getClaimsSummary_throws_whenFromIsAfterTo() {
    LocalDate from = LocalDate.of(2026, 8, 10);
    LocalDate to = LocalDate.of(2026, 8, 1);

    assertThatThrownBy(() -> accountingService.getClaimsSummary(from, to))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("posterior");
  }
}
