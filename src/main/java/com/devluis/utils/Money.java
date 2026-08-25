package com.devluis.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.devluis.entity.Servicio;

/**
 * Single place that converts the legacy {@code Float} money fields on
 * {@link Servicio} (price/discount) into a rounded {@link BigDecimal}, and
 * derives a service's net price from them.
 *
 * <p>Extracted from {@code CoveragePricingService} (which keeps using it
 * unchanged) so every other pricing feature in the "precios" admin group
 * (Promotion, Package, SessionPlan, the discount view over Servicio)
 * computes the exact same number for the exact same Servicio instead of
 * re-deriving the Float-&gt;BigDecimal conversion or the net-price formula
 * independently. That kind of duplication is exactly how "discount is a
 * percentage here but a flat amount there" bugs happen — see the apply
 * report for the cross-repo discovery (clinicore-angular's
 * precios-citas-list.component.ts) that first forced this discipline.
 */
public final class Money {
  public static final int SCALE = 2;

  private Money() {
  }

  public static BigDecimal of(Float value) {
    float v = value != null ? value : 0f;
    return BigDecimal.valueOf(v).setScale(SCALE, RoundingMode.HALF_UP);
  }

  public static BigDecimal of(BigDecimal value) {
    return (value != null ? value : BigDecimal.ZERO).setScale(SCALE, RoundingMode.HALF_UP);
  }

  public static BigDecimal zero() {
    return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
  }

  // price - discount, clamped at 0. Same formula CoveragePricingService uses
  // for the insurer-quote flow.
  public static BigDecimal netPrice(Servicio servicio) {
    BigDecimal net = of(servicio.getPrice()).subtract(of(servicio.getDiscount()));
    return net.signum() < 0 ? zero() : net;
  }

  // Share of `base` for a 0-100 `percentage`, rounded HALF_UP — same rounding
  // CoveragePricingService uses for the insurer's coinsurance share.
  public static BigDecimal percentageOf(BigDecimal base, BigDecimal percentage) {
    return base.multiply(percentage).divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP);
  }

  // Adds up several amounts that are ALREADY rounded to SCALE (every finance
  // group value is rounded once, on entry, via Money.of — see InvoiceService/
  // PaymentService). Summing already-rounded values never introduces drift,
  // so this is a plain reduction, not a second rounding pass — one place for
  // every "add up several money values" site (Invoice.total from its line
  // items, a Payment balance from its invoice's payments) instead of each
  // re-deriving reduce(ZERO, BigDecimal::add) independently.
  public static BigDecimal sum(List<BigDecimal> values) {
    if (values == null || values.isEmpty()) {
      return zero();
    }
    return values.stream().reduce(zero(), BigDecimal::add);
  }
}
