package com.devluis.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.devluis.types.ClaimStatus;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// What is billed to an INSURER rather than to the patient. Follows from an
// Invoice's TURN line items that carry a non-zero insurerCoveredAmount (see
// InvoiceLineItem) — NOT from a live PatientCoverage lookup: `insurerName`/
// `planName` are copied from those same line items' own snapshots at claim
// creation time (ClaimService#create), never re-resolved from "whichever
// coverage is active today". If a patient switches insurer between the
// invoice date and the claim date, this Claim still (correctly) bills the
// insurer that was active WHEN THE SERVICE WAS RENDERED, matching the exact
// snapshot discipline InvoiceLineItem already applies one layer down. This
// is also why Claim holds NO relation to PatientCoverage at all — a live
// reference would invite exactly the "re-read today's data" mistake this
// whole feature exists to prevent.
//
// `amountClaimed` is fixed at creation (sum of the invoice's covered line
// items) and never recalculated afterwards, even if the invoice were somehow
// changed later (it cannot be — see Invoice's docblock).
//
// Lifecycle: see ClaimStatus. A REJECTED claim does not touch the invoice's
// `total` or trigger any compensating line item — see ClaimService for the
// full reasoning: the invoice's `total` always represented the full value of
// what was rendered, so `total - sum(payments)` already remains the correct
// amount still owed after a rejection. What changes is WHO must now pay it
// (the patient, at the front desk, instead of the insurer) — not the invoice
// arithmetic.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "claims")
@Entity
public class Claim {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "invoice_id", nullable = false)
  private Invoice invoice;

  @Column(nullable = false)
  private String insurerName;

  @Column(nullable = false)
  private String planName;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amountClaimed;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  @Column(nullable = false)
  private ClaimStatus status = ClaimStatus.SUBMITTED;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime submittedAt;

  // Set when the insurer's decision comes in (ACCEPTED or REJECTED). Null
  // while still SUBMITTED.
  private OffsetDateTime decidedAt;

  // Set only when transitioning ACCEPTED -> PAID.
  private OffsetDateTime paidAt;

  // Required when rejecting (see ClaimService#reject), null otherwise.
  @Column(columnDefinition = "text")
  private String rejectionReason;
}
