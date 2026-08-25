package com.devluis.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.devluis.types.InvoiceStatus;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Issued to a Patient, with line items (see InvoiceLineItem). This is the
// financial record of what the clinic charged for — NOT a live view over
// today's catalogue. See InvoiceLineItem's docblock for the snapshot
// discipline that makes this true.
//
// `total` is the sum of the line items' `amount` at CREATION time, stored
// once and never recomputed afterwards — even if items could later be
// edited (they cannot: see below), re-deriving `total` from `items` on every
// read would reopen exactly the "recompute from today's catalogue" trap the
// whole finance group exists to avoid. `total` is the fact of what this
// invoice was issued for; it does not change for the lifetime of the record
// (the ONE exception, VOID, does not change it either — see below).
//
// No incremental "add a line to an existing invoice" endpoint: an Invoice is
// created ATOMICALLY with every one of its lines in a single call
// (InvoiceService#create) and is immutable after that (no PUT at all) —
// same "full replace, no incremental item CRUD" minimalism ServicePackage
// already uses, applied here for an even stronger reason: allowing lines to
// be added/removed after issuance is exactly the kind of after-the-fact
// rewrite this feature must never allow.
//
// DELETION: there is no @DeleteMapping anywhere in InvoiceController, same
// precedent as Encounter/Prescription in the clinical group — an invoice is
// a legal/accounting record, never hard-deletable. VOID is the only
// "removal" mechanism, and it PRESERVES the row (voidedAt/voidReason
// populated, `total` and every line item left untouched) instead of erasing
// it — voiding a fully paid invoice is refused (see InvoiceService): this
// system models no refund process, so undoing a settled invoice is
// deliberately out of reach through this API.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "invoices")
@Entity
public class Invoice {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "patient_id", nullable = false)
  private Patient patient;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal total;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  @Column(nullable = false)
  private InvoiceStatus status = InvoiceStatus.ISSUED;

  @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<InvoiceLineItem> items = new java.util.ArrayList<>();

  // No cascade: Payments and Claims are never disposable side effects of an
  // Invoice being touched — mirrors Schedule.turns' own "no cascade, a Turn
  // is never disposable" precedent.
  @OneToMany(mappedBy = "invoice")
  private List<Payment> payments;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime issuedAt;

  private OffsetDateTime voidedAt;

  private String voidReason;

  // Raw uuid, no relation — same "audit trail, no FK" idiom as
  // ClinicalAccessLog. Whoever voided it is always ROLE_ADMIN (see
  // GlobalConfig), so this is always an Operator's uuid in practice, but
  // kept untyped for the same reason ClinicalAccessLog keeps accessedByUuid
  // untyped: no join needed for what is purely a "who did this" audit fact.
  private UUID voidedByUuid;
}
