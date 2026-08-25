package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.devluis.dto.ClaimDTO;
import com.devluis.entity.Claim;
import com.devluis.entity.Invoice;
import com.devluis.entity.InvoiceLineItem;
import com.devluis.entity.Payment;
import com.devluis.repository.ClaimRepository;
import com.devluis.repository.InvoiceRepository;
import com.devluis.types.ClaimStatus;
import com.devluis.types.InvoiceLineSourceType;
import com.devluis.types.InvoiceStatus;

/**
 * The Claim lifecycle and its one hard rule: REJECTED never touches the
 * Invoice's `total` and never triggers a Payment — see
 * {@code reject_doesNotChangeTheInvoicesTotal_norTriggerAnyPayment} below,
 * which is the test that answers "where does the money come from when an
 * insurer rejects a claim" (see Claim's own docblock: nowhere new — the
 * invoice's existing `total - sum(payments)` balance already includes that
 * amount, so rejecting only changes WHO must pay it).
 */
@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

  @Mock
  private ClaimRepository claimRepository;
  @Mock
  private InvoiceRepository invoiceRepository;
  @Mock
  private PaymentService paymentService;

  private ClaimService claimService;

  @BeforeEach
  void setUp() {
    claimService = new ClaimService(claimRepository, invoiceRepository, paymentService);
  }

  private InvoiceLineItem coveredLine(BigDecimal amount, BigDecimal insurerCovered) {
    return InvoiceLineItem.builder()
        .sourceType(InvoiceLineSourceType.TURN)
        .amount(amount)
        .insurerCoveredAmount(insurerCovered)
        .patientResponsibleAmount(amount.subtract(insurerCovered))
        .insurerNameSnapshot("Seguros Sucre")
        .planNameSnapshot("Plan Oro")
        .build();
  }

  private InvoiceLineItem uncoveredLine(BigDecimal amount) {
    return InvoiceLineItem.builder()
        .sourceType(InvoiceLineSourceType.FREE_LINE)
        .amount(amount)
        .insurerCoveredAmount(BigDecimal.ZERO.setScale(2))
        .patientResponsibleAmount(amount)
        .build();
  }

  private Invoice invoiceWith(InvoiceStatus status, InvoiceLineItem... lines) {
    BigDecimal total = com.devluis.utils.Money.sum(
        java.util.Arrays.stream(lines).map(InvoiceLineItem::getAmount).toList());
    return Invoice.builder().id(1L).total(total).status(status).items(List.of(lines)).build();
  }

  // --- create() ---------------------------------------------------------

  @Test
  void create_computesAmountClaimed_asSumOfInsurerCoveredLines_andSnapshotsInsurerAndPlan() {
    Invoice invoice = invoiceWith(InvoiceStatus.ISSUED,
        coveredLine(new BigDecimal("100.00"), new BigDecimal("80.00")),
        uncoveredLine(new BigDecimal("20.00")));
    when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
    when(claimRepository.existsByInvoiceIdAndStatusIn(eq(1L), any())).thenReturn(false);
    when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> {
      Claim c = inv.getArgument(0);
      c.setId(50L);
      return c;
    });

    ClaimDTO result = claimService.create(1L);

    assertThat(result.getAmountClaimed()).isEqualByComparingTo("80.00");
    assertThat(result.getInsurerName()).isEqualTo("Seguros Sucre");
    assertThat(result.getPlanName()).isEqualTo("Plan Oro");
    assertThat(result.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
  }

  @Test
  void create_throws_whenNoLineHasInsurerCoverage() {
    Invoice invoice = invoiceWith(InvoiceStatus.ISSUED, uncoveredLine(new BigDecimal("50.00")));
    when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));

    assertThatThrownBy(() -> claimService.create(1L))
        .isInstanceOf(RuntimeException.class).hasMessageContaining("no tiene monto cubierto");
    verify(claimRepository, never()).save(any());
  }

  @Test
  void create_throws_whenInvoiceIsVoid() {
    Invoice invoice = invoiceWith(InvoiceStatus.VOID, coveredLine(new BigDecimal("100.00"), new BigDecimal("80.00")));
    when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));

    assertThatThrownBy(() -> claimService.create(1L))
        .isInstanceOf(RuntimeException.class).hasMessageContaining("anulada");
  }

  @Test
  void create_throws_whenInvoiceNotFound() {
    when(invoiceRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> claimService.create(404L))
        .isInstanceOf(RuntimeException.class).hasMessageContaining("Factura no encontrada");
  }

  @Test
  void create_throws_whenAnotherClaimIsAlreadyInFlightForThisInvoice() {
    Invoice invoice = invoiceWith(InvoiceStatus.ISSUED, coveredLine(new BigDecimal("100.00"), new BigDecimal("80.00")));
    when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
    when(claimRepository.existsByInvoiceIdAndStatusIn(eq(1L), any())).thenReturn(true);

    assertThatThrownBy(() -> claimService.create(1L))
        .isInstanceOf(RuntimeException.class).hasMessageContaining("Ya existe un reclamo");
    verify(claimRepository, never()).save(any());
  }

  // --- accept() / reject() ------------------------------------------------

  @Test
  void accept_movesSubmittedToAccepted_andSetsDecidedAt() {
    Claim claim = Claim.builder().id(1L).status(ClaimStatus.SUBMITTED).amountClaimed(new BigDecimal("80.00")).build();
    when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
    when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));

    ClaimDTO result = claimService.accept(1L);

    assertThat(result.getStatus()).isEqualTo(ClaimStatus.ACCEPTED);
    assertThat(result.getDecidedAt()).isNotNull();
  }

  @Test
  void accept_throws_whenClaimIsNotSubmitted() {
    Claim claim = Claim.builder().id(1L).status(ClaimStatus.REJECTED).build();
    when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));

    assertThatThrownBy(() -> claimService.accept(1L))
        .isInstanceOf(RuntimeException.class).hasMessageContaining("SUBMITTED");
  }

  @Test
  void reject_movesSubmittedToRejected_andRecordsTheReason() {
    Claim claim = Claim.builder().id(1L).status(ClaimStatus.SUBMITTED).amountClaimed(new BigDecimal("80.00")).build();
    when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
    when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));

    ClaimDTO result = claimService.reject(1L, "Documentación insuficiente");

    assertThat(result.getStatus()).isEqualTo(ClaimStatus.REJECTED);
    assertThat(result.getRejectionReason()).isEqualTo("Documentación insuficiente");
    assertThat(result.getDecidedAt()).isNotNull();
  }

  @Test
  void reject_throws_whenReasonIsBlank() {
    // Validated BEFORE the claim is even looked up — never reaches the
    // repository at all, so no findById stub is needed here.
    assertThatThrownBy(() -> claimService.reject(1L, "   "))
        .isInstanceOf(RuntimeException.class).hasMessageContaining("motivo");
    verify(claimRepository, never()).findById(any());
    verify(claimRepository, never()).save(any());
  }

  @Test
  void reject_throws_whenClaimIsNotSubmitted() {
    Claim claim = Claim.builder().id(1L).status(ClaimStatus.PAID).build();
    when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));

    assertThatThrownBy(() -> claimService.reject(1L, "motivo"))
        .isInstanceOf(RuntimeException.class).hasMessageContaining("SUBMITTED");
  }

  @Test
  void reject_doesNotChangeTheInvoicesTotal_norTriggerAnyPayment() {
    // This is the test for "where does the money come from when an insurer
    // rejects a claim": nowhere new. The invoice's total/balance mechanics
    // are completely untouched by a rejection.
    Claim claim = Claim.builder().id(1L).invoice(invoiceWith(InvoiceStatus.PARTIALLY_PAID,
        coveredLine(new BigDecimal("100.00"), new BigDecimal("80.00"))))
        .status(ClaimStatus.SUBMITTED).amountClaimed(new BigDecimal("80.00")).build();
    BigDecimal totalBeforeRejection = claim.getInvoice().getTotal();
    when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
    when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));

    claimService.reject(1L, "El asegurador no reconoce el procedimiento");

    assertThat(claim.getInvoice().getTotal()).isEqualByComparingTo(totalBeforeRejection);
    verifyNoInteractions(paymentService);
    verify(invoiceRepository, never()).save(any());
  }

  // --- markAsPaid() --------------------------------------------------------

  @Test
  void markAsPaid_movesAcceptedToPaid_andRecordsTheSettlementPayment() {
    Invoice invoice = invoiceWith(InvoiceStatus.ISSUED, coveredLine(new BigDecimal("100.00"), new BigDecimal("80.00")));
    Claim claim = Claim.builder().id(1L).invoice(invoice).status(ClaimStatus.ACCEPTED)
        .amountClaimed(new BigDecimal("80.00")).build();
    when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
    when(claimRepository.save(any(Claim.class))).thenAnswer(inv -> inv.getArgument(0));
    UUID staffUuid = UUID.randomUUID();
    when(paymentService.recordSettlement(eq(invoice), eq(claim), eq(staffUuid)))
        .thenReturn(Payment.builder().id(9L).amount(new BigDecimal("80.00")).build());

    ClaimDTO result = claimService.markAsPaid(1L, staffUuid);

    assertThat(result.getStatus()).isEqualTo(ClaimStatus.PAID);
    assertThat(result.getPaidAt()).isNotNull();
    verify(paymentService).recordSettlement(invoice, claim, staffUuid);
  }

  @Test
  void markAsPaid_throws_whenClaimIsNotAccepted() {
    Claim claim = Claim.builder().id(1L).status(ClaimStatus.SUBMITTED).build();
    when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));

    assertThatThrownBy(() -> claimService.markAsPaid(1L, UUID.randomUUID()))
        .isInstanceOf(RuntimeException.class).hasMessageContaining("ACCEPTED");
    verifyNoInteractions(paymentService);
  }

  // --- reads ---------------------------------------------------------------

  @Test
  void getById_throws_whenNotFound() {
    when(claimRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> claimService.getById(404L))
        .isInstanceOf(RuntimeException.class).hasMessageContaining("no encontrado");
  }

  @Test
  void search_returnsMappedClaims() {
    Claim claim = Claim.builder().id(1L).status(ClaimStatus.SUBMITTED)
        .insurerName("Seguros Sucre").planName("Plan Oro").amountClaimed(new BigDecimal("80.00"))
        .submittedAt(OffsetDateTime.now()).build();
    Pageable pageable = PageRequest.of(0, 10);
    when(claimRepository.search(1L, null, pageable)).thenReturn(new PageImpl<>(List.of(claim)));

    var result = claimService.search(1L, null, pageable);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getAmountClaimed()).isEqualByComparingTo("80.00");
  }
}
