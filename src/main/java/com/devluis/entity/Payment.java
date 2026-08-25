package com.devluis.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.devluis.types.PaymentMethod;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Money actually received against ONE Invoice. Several Payments may settle
// one Invoice (e.g. a deposit today, the remainder next week, or a patient
// slice plus a later insurer settlement) — see PaymentService for the
// invariant this class exists to protect: sum(payments for an invoice) may
// never exceed that invoice's `total`, and `total - sum(payments)` is always
// the exact outstanding balance (both sides already rounded to 2 decimals on
// entry via Money.of, so the subtraction/sum never drifts).
//
// `claim` is null for every ordinary front-desk payment. It is set ONLY when
// this Payment was auto-created by ClaimService#markAsPaid (method is then
// always INSURER_SETTLEMENT) — see PaymentMethod's docblock for why there is
// no separate payerType field.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "payments")
@Entity
public class Payment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "invoice_id", nullable = false)
  private Invoice invoice;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentMethod method;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "claim_id")
  private Claim claim;

  // Raw uuid, no relation — same "who did this" audit idiom as
  // ClinicalAccessLog/Invoice.voidedByUuid. The Operator (ROLE_EMPLOYEE or
  // ROLE_ADMIN) who registered the receipt, or who clicked "mark claim as
  // paid" for an INSURER_SETTLEMENT.
  @Column(nullable = false)
  private UUID receivedByUuid;

  // Optional free text: a transaction id, a check number, an insurer
  // settlement reference. Never parsed/validated — purely a support field.
  private String reference;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime paidAt;
}
