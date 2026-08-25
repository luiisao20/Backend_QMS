package com.devluis.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// A named plan an Insurer offers. Pricing model chosen deliberately: a
// coinsurance PERCENTAGE (the fraction of the price the insurer picks up)
// combined with an optional fixed COPAY that is charged first, coinsurance
// applying only to what remains after it — see CoveragePricingService for the
// exact formula and why an ADDITIVE percentage+copay model (patient pays
// their coinsurance share PLUS the copay on top) was rejected: at
// coveragePercentage = 0 it would charge the patient MORE than the service's
// own net price, which is not a defensible outcome.
//
// This model deliberately CANNOT express an annual aggregate cap ("insurer
// stops paying after $X/year"): enforcing that requires a running ledger of
// how much has already been paid out for this patient this year, and no
// claims/billing/consumption entity exists anywhere in this codebase to
// drive that from. Adding an unenforced `annualCapAmount` field would be
// exactly the "looks like it prices things but doesn't" trap the task asked
// to avoid — deliberately left out. See apply report.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "coverage_plans")
@Entity
public class CoveragePlan {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "insurer_id", nullable = false)
  private Insurer insurer;

  @Column(nullable = false)
  private String name;

  // 0-100 inclusive. Fraction of (netPrice - copay) the insurer covers.
  @Column(nullable = false)
  private Integer coveragePercentage;

  // Fixed amount charged before coinsurance applies. Null = no copay tier.
  // BigDecimal on purpose (unlike Servicio.price/discount, which are Float):
  // this is a brand-new field with no legacy constraint, so it gets the type
  // that is actually correct for money. See apply report.
  @Column(precision = 12, scale = 2)
  private BigDecimal copayAmount;

  @OneToMany(mappedBy = "plan")
  private List<PatientCoverage> patientCoverages;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
