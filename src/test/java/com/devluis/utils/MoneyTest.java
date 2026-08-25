package com.devluis.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.devluis.entity.Servicio;

/**
 * Single conversion point for the legacy Float money fields on
 * {@link Servicio}. Every new pricing feature (Promotion, Package,
 * SessionPlan, the discount view over Servicio) reuses this instead of
 * re-deriving the Float->BigDecimal rounding or the net-price formula —
 * see the class docblock.
 */
class MoneyTest {

  @Test
  void of_float_roundsToTwoDecimals_halfUp() {
    assertThat(Money.of(10.005f)).isEqualByComparingTo("10.01");
  }

  @Test
  void of_nullFloat_isZero() {
    assertThat(Money.of((Float) null)).isEqualByComparingTo("0.00");
  }

  @Test
  void of_nullBigDecimal_isZero() {
    assertThat(Money.of((BigDecimal) null)).isEqualByComparingTo("0.00");
  }

  @Test
  void of_bigDecimal_roundsToTwoDecimals() {
    assertThat(Money.of(new BigDecimal("5.005"))).isEqualByComparingTo("5.01");
  }

  @Test
  void zero_isScaledToTwoDecimals() {
    assertThat(Money.zero()).isEqualByComparingTo("0.00");
    assertThat(Money.zero().scale()).isEqualTo(2);
  }

  @Test
  void netPrice_subtractsDiscountFromPrice() {
    Servicio servicio = Servicio.builder().price(100f).discount(10f).build();

    assertThat(Money.netPrice(servicio)).isEqualByComparingTo("90.00");
  }

  @Test
  void netPrice_withNoDiscount_equalsPrice() {
    Servicio servicio = Servicio.builder().price(100f).discount(null).build();

    assertThat(Money.netPrice(servicio)).isEqualByComparingTo("100.00");
  }

  @Test
  void netPrice_discountLargerThanPrice_clampsAtZero_insteadOfGoingNegative() {
    Servicio servicio = Servicio.builder().price(20f).discount(50f).build();

    assertThat(Money.netPrice(servicio)).isEqualByComparingTo("0.00");
  }

  @Test
  void percentageOf_computesShare_roundedHalfUp() {
    assertThat(Money.percentageOf(new BigDecimal("10.00"), new BigDecimal("33")))
        .isEqualByComparingTo("3.30");
  }

  @Test
  void percentageOf_exactHalfCent_roundsUp() {
    // 0.05 * 50% = 0.025 -> must round UP to 0.03, not down to 0.02 (same
    // boundary CoveragePricingServiceTest proves for the insurer split).
    assertThat(Money.percentageOf(new BigDecimal("0.05"), new BigDecimal("50")))
        .isEqualByComparingTo("0.03");
  }

  // --- sum: used by InvoiceService (line items -> total) and PaymentService
  // (payments -> balance) so every "add up several already-rounded amounts"
  // site in the finance group shares one, tested implementation instead of
  // re-deriving reduce(ZERO, BigDecimal::add) independently.

  @Test
  void sum_addsSeveralAlreadyRoundedAmounts_withNoDrift() {
    assertThat(Money.sum(List.of(new BigDecimal("3.33"), new BigDecimal("3.33"), new BigDecimal("3.34"))))
        .isEqualByComparingTo("10.00");
  }

  @Test
  void sum_ofEmptyList_isZero() {
    assertThat(Money.sum(List.of())).isEqualByComparingTo("0.00");
  }

  @Test
  void sum_ofNull_isZero() {
    assertThat(Money.sum(null)).isEqualByComparingTo("0.00");
  }
}
