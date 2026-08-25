package com.devluis.services;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devluis.dto.ClaimDTO;
import com.devluis.entity.Claim;
import com.devluis.entity.Invoice;
import com.devluis.entity.InvoiceLineItem;
import com.devluis.repository.ClaimRepository;
import com.devluis.repository.InvoiceRepository;
import com.devluis.types.ClaimStatus;
import com.devluis.types.InvoiceStatus;
import com.devluis.utils.Money;

import lombok.Data;

/**
 * What is billed to an INSURER — see Claim's own docblock for why this class
 * holds no relation to PatientCoverage at all: {@code insurerName}/
 * {@code planName}/{@code amountClaimed} are derived ONCE, at
 * {@link #create}, from the invoice's OWN line-item snapshots
 * (never from "whichever coverage is active today").
 *
 * <p>{@link #reject} is deliberately the simplest possible operation: it
 * flips {@code status} and records a reason. It does NOT touch
 * {@code Invoice.total}, does NOT create a compensating line item, and does
 * NOT call {@link PaymentService} at all (see
 * {@code ClaimServiceTest#reject_doesNotChangeTheInvoicesTotal_norTriggerAnyPayment}).
 * That is not an oversight: the invoice's {@code total} always represented
 * the FULL value of what was rendered — the insurer's expected share was
 * never subtracted out of it — so {@code total - sum(payments)} already
 * remains the exact amount still owed after a rejection. What changes is WHO
 * must now pay it (the patient, at the front desk, instead of the insurer),
 * which is a front-desk workflow fact, not an accounting mutation.
 */
@Service
@Data
public class ClaimService {
  private final ClaimRepository claimRepository;
  private final InvoiceRepository invoiceRepository;
  private final PaymentService paymentService;

  // Deliberately NOT a set of "resubmittable" states beyond REJECTED: a
  // SUBMITTED or ACCEPTED claim already represents money the clinic is
  // actively expecting from an insurer for this invoice, and a PAID claim
  // means it already arrived — none of those should be double-claimed. A
  // REJECTED claim is terminal for itself but does NOT block a brand-new
  // claim afterwards (e.g. resubmitting with corrected documentation) — see
  // ClaimStatus's own docblock for why this, not a full dispute/resubmission
  // workflow, is the deliberately minimal answer.
  private static final List<ClaimStatus> BLOCKING_STATUSES =
      List.of(ClaimStatus.SUBMITTED, ClaimStatus.ACCEPTED, ClaimStatus.PAID);

  @Transactional
  public ClaimDTO create(Long invoiceId) {
    Invoice invoice = findInvoiceOrThrow(invoiceId);
    if (invoice.getStatus() == InvoiceStatus.VOID) {
      throw new RuntimeException("No se puede reclamar una factura anulada");
    }
    if (claimRepository.existsByInvoiceIdAndStatusIn(invoiceId, BLOCKING_STATUSES)) {
      throw new RuntimeException("Ya existe un reclamo en curso o pagado para esta factura");
    }

    List<InvoiceLineItem> coveredLines = invoice.getItems() == null ? List.of() : invoice.getItems().stream()
        .filter(item -> item.getInsurerCoveredAmount() != null && item.getInsurerCoveredAmount().signum() > 0)
        .toList();
    if (coveredLines.isEmpty()) {
      throw new RuntimeException("La factura no tiene monto cubierto por un asegurador para reclamar");
    }

    BigDecimal amountClaimed = Money.sum(coveredLines.stream().map(InvoiceLineItem::getInsurerCoveredAmount).toList());
    InvoiceLineItem firstCovered = coveredLines.get(0);

    Claim claim = Claim.builder()
        .invoice(invoice)
        .insurerName(firstCovered.getInsurerNameSnapshot())
        .planName(firstCovered.getPlanNameSnapshot())
        .amountClaimed(amountClaimed)
        .status(ClaimStatus.SUBMITTED)
        .build();

    Claim saved = claimRepository.save(claim);
    return mapToDTO(saved);
  }

  public ClaimDTO getById(Long id) {
    return mapToDTO(findByIdOrThrow(id));
  }

  public Page<ClaimDTO> search(Long invoiceId, ClaimStatus status, Pageable pageable) {
    return claimRepository.search(invoiceId, status, pageable).map(this::mapToDTO);
  }

  @Transactional
  public ClaimDTO accept(Long id) {
    Claim claim = findByIdOrThrow(id);
    assertStatus(claim, ClaimStatus.SUBMITTED);

    claim.setStatus(ClaimStatus.ACCEPTED);
    claim.setDecidedAt(OffsetDateTime.now());

    return mapToDTO(claimRepository.save(claim));
  }

  @Transactional
  public ClaimDTO reject(Long id, String reason) {
    if (reason == null || reason.isBlank()) {
      throw new RuntimeException("El motivo de rechazo es obligatorio");
    }
    Claim claim = findByIdOrThrow(id);
    assertStatus(claim, ClaimStatus.SUBMITTED);

    claim.setStatus(ClaimStatus.REJECTED);
    claim.setDecidedAt(OffsetDateTime.now());
    claim.setRejectionReason(reason);

    return mapToDTO(claimRepository.save(claim));
  }

  @Transactional
  public ClaimDTO markAsPaid(Long id, UUID receivedByUuid) {
    Claim claim = findByIdOrThrow(id);
    assertStatus(claim, ClaimStatus.ACCEPTED);

    paymentService.recordSettlement(claim.getInvoice(), claim, receivedByUuid);

    claim.setStatus(ClaimStatus.PAID);
    claim.setPaidAt(OffsetDateTime.now());

    return mapToDTO(claimRepository.save(claim));
  }

  private void assertStatus(Claim claim, ClaimStatus expected) {
    if (claim.getStatus() != expected) {
      throw new RuntimeException(
          "El reclamo debe estar en estado " + expected + " para esta operación (estado actual: " + claim.getStatus() + ")");
    }
  }

  private Claim findByIdOrThrow(Long id) {
    return claimRepository.findById(id).orElseThrow(() -> new RuntimeException("Reclamo no encontrado"));
  }

  private Invoice findInvoiceOrThrow(Long id) {
    return invoiceRepository.findById(id).orElseThrow(() -> new RuntimeException("Factura no encontrada"));
  }

  private ClaimDTO mapToDTO(Claim entity) {
    return ClaimDTO.builder()
        .id(entity.getId())
        .invoiceId(entity.getInvoice() != null ? entity.getInvoice().getId() : null)
        .insurerName(entity.getInsurerName())
        .planName(entity.getPlanName())
        .amountClaimed(entity.getAmountClaimed())
        .status(entity.getStatus())
        .submittedAt(entity.getSubmittedAt())
        .decidedAt(entity.getDecidedAt())
        .paidAt(entity.getPaidAt())
        .rejectionReason(entity.getRejectionReason())
        .build();
  }
}
