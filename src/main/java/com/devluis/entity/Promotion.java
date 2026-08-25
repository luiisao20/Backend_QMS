package com.devluis.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.devluis.types.DiscountType;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// A time-bounded price reduction on ONE Servicio ("20% off teeth whitening
// in August"). This is the "precios/promociones" admin destination, and is
// deliberately the ONLY time-bounded discount mechanism in this codebase —
// see Servicio's docblock / the apply report for why "precios/descuentos"
// does NOT get its own second time-bounded catalog: that would duplicate
// exactly what this entity already does and reopen the "two sources of
// truth for what a service costs" problem the task explicitly warned
// against. Servicio.discount stays the STANDING, non-expiring reduction;
// Promotion is the CAMPAIGN, expiring one.
//
// AT MOST ONE Promotion may be active for a given Servicio on any given
// date — enforced in PromotionService (rejects create/update if the new
// date range [startDate, endDate] overlaps an existing Promotion for the
// same Servicio), not by a DB constraint (no database exists in this
// environment to add one against). This is a deliberate, explicit choice
// over two alternatives considered: silently deactivating the older
// promotion (surprising — an admin could think a promotion is still running
// after a new one silently killed it), or allowing overlaps and stacking
// both reductions (aggressive double-discounting nothing here asked for).
// Rejecting at write time means the database can never contain two
// simultaneously-active promotions for one service, so
// CoveragePricingService never needs a tie-break rule when resolving "the"
// active promotion for a service on a given date.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "promotions")
@Entity
public class Promotion {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "service_id", nullable = false)
  private Servicio servicio;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DiscountType discountType;

  // PERCENTAGE: 0-100 (exclusive of 0, see PromotionDTO validation).
  // FIXED_AMOUNT: a currency amount, same unit as Servicio.price.
  // BigDecimal on purpose (unlike Servicio.price/discount, which are Float):
  // this is a brand-new field with no legacy constraint, same reasoning as
  // CoveragePlan.copayAmount.
  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal discountValue;

  // Inclusive on both ends, same convention as TimeOff.startDate/endDate.
  @Column(nullable = false)
  private LocalDate startDate;

  @Column(nullable = false)
  private LocalDate endDate;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
