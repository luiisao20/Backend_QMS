package com.devluis.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devluis.dto.PaymentDTO;
import com.devluis.entity.Claim;
import com.devluis.entity.Invoice;
import com.devluis.entity.Payment;
import com.devluis.repository.InvoiceRepository;
import com.devluis.repository.PaymentRepository;
import com.devluis.types.InvoiceStatus;
import com.devluis.types.PaymentMethod;
import com.devluis.utils.Money;

import lombok.Data;

/**
 * Owns the ONE invariant of the finance group's money-received side: the sum
 * of a Payment's for an Invoice may never exceed that Invoice's `total`, and
 * `total - sum(payments)` is always the exact outstanding balance — see
 * Payment's own docblock.
 *
 * <p>Every incoming amount is rounded EXACTLY ONCE, immediately, via
 * {@link Money#of(BigDecimal)} before it is compared against the balance or
 * persisted — never re-rounded afterwards. Existing payments read back from
 * the repository are summed with {@link Money#sum(List)}, which is a plain
 * reduction over already-rounded values (no second rounding pass), so the
 * comparison against `total` (itself rounded once, at Invoice creation) can
 * never drift.
 *
 * <p>{@code Invoice.status} is NEVER set by a caller directly — every
 * successful payment recomputes it here as a pure function of
 * (total, totalPaidAfterThisPayment): ISSUED while nothing has been paid,
 * PARTIALLY_PAID while some but not all of `total` has been collected, PAID
 * once the sum reaches (or, defensively, exceeds — which the balance check
 * above makes impossible) `total`. VOID is never touched by this class: an
 * Invoice already in VOID status is refused outright (see {@link #record}).
 */
@Service
@Data
public class PaymentService {
  private final PaymentRepository paymentRepository;
  private final InvoiceRepository invoiceRepository;

  @Transactional
  public PaymentDTO create(Long invoiceId, PaymentDTO dto, Authentication auth) {
    Invoice invoice = findInvoiceOrThrow(invoiceId);
    UUID receivedByUuid = UUID.fromString(auth.getName());
    BigDecimal amount = Money.of(dto.getAmount());

    Payment saved = record(invoice, amount, dto.getMethod(), receivedByUuid, dto.getReference(), null);
    return mapToDTO(saved);
  }

  public List<PaymentDTO> getForInvoice(Long invoiceId) {
    return paymentRepository.findByInvoiceIdOrderByPaidAtAsc(invoiceId).stream().map(this::mapToDTO).toList();
  }

  // Shared by InvoiceService (to show the outstanding balance on an
  // InvoiceDTO) and internally here — the ONE place this subtraction
  // happens.
  public BigDecimal getBalance(Invoice invoice) {
    return invoice.getTotal().subtract(sumPayments(invoice.getId()));
  }

  // Called by ClaimService#markAsPaid — the insurer's settlement IS a
  // Payment, so it goes through the exact same invariant check and status
  // recomputation as any other payment, instead of a parallel code path
  // that could let a claim payoff silently exceed the invoice total.
  @Transactional
  public Payment recordSettlement(Invoice invoice, Claim claim, UUID receivedByUuid) {
    return record(invoice, claim.getAmountClaimed(), PaymentMethod.INSURER_SETTLEMENT,
        receivedByUuid, "Reclamo #" + claim.getId(), claim);
  }

  private Payment record(
      Invoice invoice, BigDecimal amount, PaymentMethod method, UUID receivedByUuid, String reference, Claim claim) {
    if (invoice.getStatus() == InvoiceStatus.VOID) {
      throw new RuntimeException("No se puede registrar un pago sobre una factura anulada");
    }

    BigDecimal alreadyPaid = sumPayments(invoice.getId());
    BigDecimal balance = invoice.getTotal().subtract(alreadyPaid);
    if (amount.compareTo(balance) > 0) {
      throw new RuntimeException("El pago excede el saldo pendiente de la factura. Saldo actual: " + balance);
    }

    Payment payment = Payment.builder()
        .invoice(invoice)
        .amount(amount)
        .method(method)
        .receivedByUuid(receivedByUuid)
        .reference(reference)
        .claim(claim)
        .build();
    Payment saved = paymentRepository.save(payment);

    BigDecimal totalPaidAfter = alreadyPaid.add(amount);
    invoice.setStatus(resolveStatus(invoice.getTotal(), totalPaidAfter));
    invoiceRepository.save(invoice);

    return saved;
  }

  private BigDecimal sumPayments(Long invoiceId) {
    return Money.sum(paymentRepository.findByInvoiceIdOrderByPaidAtAsc(invoiceId)
        .stream().map(Payment::getAmount).toList());
  }

  private InvoiceStatus resolveStatus(BigDecimal total, BigDecimal totalPaid) {
    if (totalPaid.compareTo(total) >= 0) {
      return InvoiceStatus.PAID;
    }
    if (totalPaid.signum() > 0) {
      return InvoiceStatus.PARTIALLY_PAID;
    }
    return InvoiceStatus.ISSUED;
  }

  private Invoice findInvoiceOrThrow(Long id) {
    return invoiceRepository.findById(id).orElseThrow(() -> new RuntimeException("Factura no encontrada"));
  }

  private PaymentDTO mapToDTO(Payment entity) {
    return PaymentDTO.builder()
        .id(entity.getId())
        .amount(entity.getAmount())
        .method(entity.getMethod())
        .reference(entity.getReference())
        .receivedByUuid(entity.getReceivedByUuid())
        .paidAt(entity.getPaidAt())
        .claimId(entity.getClaim() != null ? entity.getClaim().getId() : null)
        .build();
  }
}
