package com.devluis.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// "precios/sesiones": N sessions of ONE Servicio sold as a bundle at one
// combined price (physiotherapy, dentistry cleanings, etc). `price` is set
// explicitly by the admin, same "not auto-derived" reasoning as
// ServicePackage.price — bulk pricing is a business decision, not
// (sessionCount * Servicio.netPrice).
//
// DELIBERATELY NOT modelled here: session consumption ("patient X has 4 of
// 10 sessions left"). That needs a ledger tying purchased plans to
// individual appointments/turns, and no such entity exists anywhere in this
// codebase to drive it from — same "no requirement, no ledger to hang it
// on" reasoning CoveragePlan used to skip an annual aggregate cap. The
// "precios/sesiones" screen can show the CATALOG (available plans, price,
// price-per-session, savings vs buying individually) but CANNOT show a
// specific patient's remaining balance — see apply report.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "session_plans")
@Entity
public class SessionPlan {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "service_id", nullable = false)
  private Servicio servicio;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private Integer sessionCount;

  // BigDecimal on purpose (unlike Servicio.price/discount, which are Float):
  // brand-new field, no legacy constraint, same reasoning as
  // CoveragePlan.copayAmount.
  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal price;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
