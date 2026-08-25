package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.devluis.dto.PaymentDTO;
import com.devluis.entity.Claim;
import com.devluis.entity.Invoice;
import com.devluis.entity.Payment;
import com.devluis.repository.InvoiceRepository;
import com.devluis.repository.PaymentRepository;
import com.devluis.types.InvoiceStatus;
import com.devluis.types.PaymentMethod;

/**
 * The payment invariant: sum(payments for an invoice) may never exceed that
 * invoice's `total`, and `total - sum(payments)` is always the exact
 * outstanding balance — see Payment's own docblock. Every incoming amount is
 * rounded ONCE, immediately, via {@link com.devluis.utils.Money#of(BigDecimal)}
 * (see the class docblock on {@code PaymentService} for why).
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock
  private PaymentRepository paymentRepository;
  @Mock
  private InvoiceRepository invoiceRepository;

  private PaymentService paymentService;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    paymentService = new PaymentService(paymentRepository, invoiceRepository);
  }

  private Invoice invoiceWith(BigDecimal total, InvoiceStatus status) {
    return Invoice.builder().id(1L).total(total).status(status).build();
  }

  private Payment paymentOf(BigDecimal amount) {
    return Payment.builder().amount(amount).build();
  }

  private Authentication staffAuth(UUID uuid) {
    return new UsernamePasswordAuthenticationToken(
        uuid.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
  }

  @Test
  void create_recordsPayment_andMovesInvoiceToPartiallyPaid_whenBalanceRemains() {
    Invoice invoice = invoiceWith(new BigDecimal("100.00"), InvoiceStatus.ISSUED);
    when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
    when(paymentRepository.findByInvoiceIdOrderByPaidAtAsc(1L)).thenReturn(List.of());
    when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
      Payment p = inv.getArgument(0);
      p.setId(10L);
      p.setPaidAt(OffsetDateTime.now());
      return p;
    });
    UUID staffUuid = UUID.randomUUID();

    PaymentDTO dto = PaymentDTO.builder().amount(new BigDecimal("30.00")).method(PaymentMethod.CASH).build();
    PaymentDTO result = paymentService.create(1L, dto, staffAuth(staffUuid));

    assertThat(result.getAmount()).isEqualByComparingTo("30.00");
    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
    verify(invoiceRepository).save(invoice);
  }

  @Test
  void create_movesInvoiceToPaid_whenPaymentCompletesTheTotalExactly() {
    Invoice invoice = invoiceWith(new BigDecimal("100.00"), InvoiceStatus.PARTIALLY_PAID);
    when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
    when(paymentRepository.findByInvoiceIdOrderByPaidAtAsc(1L)).thenReturn(List.of(paymentOf(new BigDecimal("60.00"))));
    when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

    PaymentDTO dto = PaymentDTO.builder().amount(new BigDecimal("40.00")).method(PaymentMethod.CARD).build();
    paymentService.create(1L, dto, staffAuth(UUID.randomUUID()));

    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
  }

  @Test
  void create_throws_whenPaymentExceedsTheOutstandingBalance() {
    Invoice invoice = invoiceWith(new BigDecimal("100.00"), InvoiceStatus.ISSUED);
    when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
    when(paymentRepository.findByInvoiceIdOrderByPaidAtAsc(1L)).thenReturn(List.of(paymentOf(new BigDecimal("80.00"))));

    PaymentDTO dto = PaymentDTO.builder().amount(new BigDecimal("20.01")).method(PaymentMethod.CASH).build();

    assertThatThrownBy(() -> paymentService.create(1L, dto, staffAuth(UUID.randomUUID())))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("excede el saldo pendiente");

    verify(paymentRepository, never()).save(any());
    verify(invoiceRepository, never()).save(any());
  }

  @Test
  void create_roundingBoundary_paymentThatExactlyClosesTheBalance_succeeds() {
    // 3.33 + 3.33 already paid on a 10.00 total -> exact remaining balance is
    // 3.34. A payment of exactly 3.34 must succeed and fully close it.
    Invoice invoice = invoiceWith(new BigDecimal("10.00"), InvoiceStatus.PARTIALLY_PAID);
    when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
    when(paymentRepository.findByInvoiceIdOrderByPaidAtAsc(1L))
        .thenReturn(List.of(paymentOf(new BigDecimal("3.33")), paymentOf(new BigDecimal("3.33"))));
    when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

    PaymentDTO dto = PaymentDTO.builder().amount(new BigDecimal("3.34")).method(PaymentMethod.CASH).build();
    PaymentDTO result = paymentService.create(1L, dto, staffAuth(UUID.randomUUID()));

    assertThat(result.getAmount()).isEqualByComparingTo("3.34");
    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
  }

  @Test
  void create_roundingBoundary_evenOneMoreCentAfterBalanceIsExactlyZero_isRejected() {
    // Balance is EXACTLY 0.00 (10.00 total, 10.00 already paid) — even a
    // 0.01 payment must be rejected, not silently accepted due to rounding.
    Invoice invoice = invoiceWith(new BigDecimal("10.00"), InvoiceStatus.PAID);
    when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
    when(paymentRepository.findByInvoiceIdOrderByPaidAtAsc(1L)).thenReturn(List.of(
        paymentOf(new BigDecimal("3.33")), paymentOf(new BigDecimal("3.33")), paymentOf(new BigDecimal("3.34"))));

    PaymentDTO dto = PaymentDTO.builder().amount(new BigDecimal("0.01")).method(PaymentMethod.CASH).build();

    assertThatThrownBy(() -> paymentService.create(1L, dto, staffAuth(UUID.randomUUID())))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("excede el saldo pendiente");
  }

  @Test
  void create_throws_whenInvoiceIsVoid() {
    Invoice invoice = invoiceWith(new BigDecimal("100.00"), InvoiceStatus.VOID);
    when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));

    PaymentDTO dto = PaymentDTO.builder().amount(new BigDecimal("10.00")).method(PaymentMethod.CASH).build();

    assertThatThrownBy(() -> paymentService.create(1L, dto, staffAuth(UUID.randomUUID())))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("anulada");

    verify(paymentRepository, never()).save(any());
  }

  @Test
  void create_roundsTheIncomingAmountImmediately_toTwoDecimalsHalfUp() {
    Invoice invoice = invoiceWith(new BigDecimal("100.00"), InvoiceStatus.ISSUED);
    when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
    when(paymentRepository.findByInvoiceIdOrderByPaidAtAsc(1L)).thenReturn(List.of());
    when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

    // 5.005 has no exact 2-decimal representation as entered — must be
    // rounded to 5.01 (HALF_UP) BEFORE it is ever compared/stored, same
    // discipline as CoveragePricingService/Money everywhere else.
    PaymentDTO dto = PaymentDTO.builder().amount(new BigDecimal("5.005")).method(PaymentMethod.CASH).build();
    PaymentDTO result = paymentService.create(1L, dto, staffAuth(UUID.randomUUID()));

    ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository).save(captor.capture());
    assertThat(captor.getValue().getAmount()).isEqualByComparingTo("5.01");
    assertThat(result.getAmount()).isEqualByComparingTo("5.01");
  }

  @Test
  void create_throws_whenInvoiceNotFound() {
    when(invoiceRepository.findById(404L)).thenReturn(Optional.empty());

    PaymentDTO dto = PaymentDTO.builder().amount(new BigDecimal("10.00")).method(PaymentMethod.CASH).build();

    assertThatThrownBy(() -> paymentService.create(404L, dto, staffAuth(UUID.randomUUID())))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Factura no encontrada");
  }

  @Test
  void getForInvoice_returnsPaymentsMappedInTheRepositorysOrder() {
    Payment p1 = Payment.builder().id(1L).amount(new BigDecimal("10.00")).method(PaymentMethod.CASH).build();
    Payment p2 = Payment.builder().id(2L).amount(new BigDecimal("20.00")).method(PaymentMethod.CARD).build();
    when(paymentRepository.findByInvoiceIdOrderByPaidAtAsc(1L)).thenReturn(List.of(p1, p2));

    List<PaymentDTO> result = paymentService.getForInvoice(1L);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getAmount()).isEqualByComparingTo("10.00");
    assertThat(result.get(1).getAmount()).isEqualByComparingTo("20.00");
  }

  @Test
  void getBalance_isExactlyTotalMinusSumOfPayments() {
    Invoice invoice = invoiceWith(new BigDecimal("100.00"), InvoiceStatus.PARTIALLY_PAID);
    when(paymentRepository.findByInvoiceIdOrderByPaidAtAsc(1L))
        .thenReturn(List.of(paymentOf(new BigDecimal("30.00")), paymentOf(new BigDecimal("15.50"))));

    BigDecimal balance = paymentService.getBalance(invoice);

    assertThat(balance).isEqualByComparingTo("54.50");
  }

  @Test
  void recordSettlement_createsAnInsurerSettlementPayment_referencingTheClaim() {
    Invoice invoice = invoiceWith(new BigDecimal("100.00"), InvoiceStatus.ISSUED);
    Claim claim = Claim.builder().id(7L).amountClaimed(new BigDecimal("80.00")).build();
    when(paymentRepository.findByInvoiceIdOrderByPaidAtAsc(1L)).thenReturn(List.of());
    when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    UUID staffUuid = UUID.randomUUID();

    Payment result = paymentService.recordSettlement(invoice, claim, staffUuid);

    assertThat(result.getAmount()).isEqualByComparingTo("80.00");
    assertThat(result.getMethod()).isEqualTo(PaymentMethod.INSURER_SETTLEMENT);
    assertThat(result.getClaim()).isSameAs(claim);
    assertThat(result.getReceivedByUuid()).isEqualTo(staffUuid);
    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
    verify(invoiceRepository, times(1)).save(invoice);
  }
}
