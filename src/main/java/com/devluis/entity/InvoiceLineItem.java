package com.devluis.entity;

import java.math.BigDecimal;

import com.devluis.types.InvoiceLineSourceType;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// ONE charge on an Invoice. This is the entity that carries the whole
// "snapshot, never recompute" discipline for the finance group:
//
// - `description`, `amount`, `insurerCoveredAmount`, `patientResponsibleAmount`,
//   `insurerNameSnapshot` and `planNameSnapshot` are all copied in at
//   creation time (InvoiceService#buildLineItem) from whatever Servicio /
//   CoveragePricingService quote / ServicePackage / SessionPlan / free-text
//   entry was in effect AT THAT MOMENT. None of them are ever re-derived on
//   a later read. If an admin edits a Servicio's price, a CoveragePlan's
//   percentage, or a Promotion tomorrow, every InvoiceLineItem created today
//   keeps showing today's numbers — that is the entire point of this class.
//
// - `sourceType` + `sourceId` are kept ONLY for traceability ("which Turn /
//   ServicePackage / SessionPlan did this line come from") and are
//   DELIBERATELY a plain Long, not a JPA relation. A live relation here
//   would be an open invitation for a future change to call
//   `.getServicio().getPrice()` on it and reintroduce the exact bug this
//   design prevents. `sourceId` is null for FREE_LINE.
//
// - `insurerCoveredAmount + patientResponsibleAmount == amount` holds BY
//   CONSTRUCTION (patientResponsibleAmount is always computed as
//   `amount.subtract(insurerCoveredAmount)`, never entered or rounded
//   independently) — the same invariant-by-subtraction discipline
//   CoveragePricingService itself already uses for insurerCovers/patientPays,
//   extended one layer further into the persisted record.
//
// - Only a TURN line can have `insurerCoveredAmount > 0` (see
//   InvoiceLineSourceType). PACKAGE, SESSION_PLAN and FREE_LINE lines always
//   have `insurerCoveredAmount = 0` and `insurerNameSnapshot/planNameSnapshot
//   = null` — there is no coverage concept for a bundle price or a manual
//   charge in this codebase (see ServicePackage/SessionPlan docblocks).
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "invoice_line_items")
@Entity
public class InvoiceLineItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "invoice_id", nullable = false)
  private Invoice invoice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InvoiceLineSourceType sourceType;

  // Traceability only — see class docblock. Null for FREE_LINE.
  private Long sourceId;

  @Column(nullable = false, columnDefinition = "text")
  private String description;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, precision = 12, scale = 2)
  @Builder.Default
  private BigDecimal insurerCoveredAmount = BigDecimal.ZERO;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal patientResponsibleAmount;

  // Snapshots of WHICH insurer/plan was expected to cover this line, at the
  // moment the invoice was issued — see class docblock. Both null when
  // insurerCoveredAmount is zero.
  private String insurerNameSnapshot;

  private String planNameSnapshot;
}
