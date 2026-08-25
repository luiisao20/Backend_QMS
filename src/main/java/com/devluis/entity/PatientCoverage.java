package com.devluis.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Links a Patient to the CoveragePlan they hold, with the policy number and
// validity window a physical insurance card carries. A patient may accumulate
// several of these over the years (switched insurer, renewed with a new
// policy number); AT MOST ONE may be `active` at a time — enforced in
// PatientCoverageService (auto-deactivates any previously-active record for
// the same patient on save), not by a DB constraint, since no database exists
// in this environment to migrate/verify a partial unique index against.
//
// Only one active record is allowed, deliberately: this system has no
// coordination-of-benefits model (which payer is primary when a patient holds
// two policies at once), so the pricing quote (CoveragePricingService) needs
// a single unambiguous answer to "which coverage applies right now" — same
// "no requirement is driving this yet" reasoning the Encounter entity used to
// defer modelling vitals.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "patient_coverages")
@Entity
public class PatientCoverage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "patient_id", nullable = false)
  private Patient patient;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "plan_id", nullable = false)
  private CoveragePlan plan;

  @Column(nullable = false)
  private String policyNumber;

  @Column(nullable = false)
  private LocalDate validFrom;

  // Nullable on purpose: an ongoing policy with no known end date yet.
  private LocalDate validUntil;

  @Column(nullable = false)
  private boolean active;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
