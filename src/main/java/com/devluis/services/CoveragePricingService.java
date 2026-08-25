package com.devluis.services;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.devluis.dto.CoverageQuoteDTO;
import com.devluis.entity.CoveragePlan;
import com.devluis.entity.PatientCoverage;
import com.devluis.entity.Promotion;
import com.devluis.entity.Servicio;
import com.devluis.types.DiscountType;
import com.devluis.utils.Money;

/**
 * Pure calculator: "what would this patient pay for this service, right
 * now". No repositories, no I/O — {@link PatientCoverageService} resolves the
 * {@link Servicio}, the patient's currently active {@link PatientCoverage}
 * (or {@code null}), and the service's currently active {@link Promotion}
 * (or {@code null}), and hands them here.
 *
 * <p><b>Model</b>: {@code coveragePercentage} and {@code copayAmount} are
 * MUTUALLY EXCLUSIVE levers on the same plan — copay takes precedence when
 * both are set. Two other combinations were tried during design and rejected:
 * <ul>
 *   <li>ADDITIVE (coinsurance share PLUS the copay on top): at
 *       {@code coveragePercentage = 0} this charges the patient MORE than
 *       the service's own net price — indefensible.</li>
 *   <li>"copay first, coinsurance on the remainder": at
 *       {@code coveragePercentage = 0} the copay becomes a complete no-op
 *       (patient still pays the full net price), which contradicts what a
 *       "copay plan" is supposed to mean.</li>
 * </ul>
 * Mutually-exclusive levers avoid both traps. What this CANNOT express: a
 * plan with both a copay AND additional coinsurance beyond it (real
 * insurance sometimes tiers these) — this system prices one service at one
 * price, with no "base visit fee vs. additional charges" tiering to hang
 * that on. See CoveragePlan's docblock and the apply report.
 *
 * <p><b>Promotion</b>: an optional, currently-active {@link Promotion} for
 * the same Servicio is applied AFTER the flat {@code discount} column and
 * BEFORE insurance evaluates its share — computationally it plays the exact
 * same role {@code discount} already plays (a further reduction of the
 * taxable base), just time-bounded and admin-catalogued instead of a static
 * column value. This is a deliberate choice, not the only possible one: an
 * "insurance first, promotion only on what the patient would have paid"
 * ordering was considered and rejected because it makes a promotion's
 * effective discount depend on a specific patient's coverage, which
 * contradicts what an advertised "20% off" means to a self-pay prospective
 * patient reading the public catalogue. See Promotion's docblock and the
 * apply report.
 *
 * <p><b>Rounding</b>: every monetary value is rounded to 2 decimals
 * (HALF_UP) the moment it is produced, and never re-rounded afterwards.
 * {@code insurerCovers + patientPays == netPrice} always holds exactly
 * because both are derived from the same already-rounded {@code netPrice}/
 * {@code copay} pair by subtraction, never by two independent roundings.
 *
 * <p><b>No coverage</b>: returns a normal 200-shaped quote with
 * {@code hasCoverage = false} and {@code patientPays = netPrice} — this is
 * an expected, valid outcome (a self-pay patient), not an error.
 */
@Service
public class CoveragePricingService {

  // Pre-existing call sites (and their tests) keep working unchanged: no
  // promotion in play.
  public CoverageQuoteDTO quote(Servicio servicio, PatientCoverage activeCoverage) {
    return quote(servicio, activeCoverage, null);
  }

  public CoverageQuoteDTO quote(Servicio servicio, PatientCoverage activeCoverage, Promotion activePromotion) {
    BigDecimal listPrice = Money.of(servicio.getPrice());
    BigDecimal discount = Money.of(servicio.getDiscount());
    BigDecimal preNetPrice = Money.netPrice(servicio);

    String promotionName = null;
    BigDecimal promotionReduction = null;
    BigDecimal netPrice = preNetPrice;
    if (activePromotion != null) {
      promotionName = activePromotion.getName();
      promotionReduction = activePromotion.getDiscountType() == DiscountType.PERCENTAGE
          ? Money.percentageOf(preNetPrice, activePromotion.getDiscountValue())
          : Money.of(activePromotion.getDiscountValue());
      // Never let a misconfigured promotion push the net price negative —
      // same defensive clamp idiom as the copay clamp below.
      promotionReduction = promotionReduction.min(preNetPrice);
      netPrice = preNetPrice.subtract(promotionReduction);
    }

    if (activeCoverage == null || activeCoverage.getPlan() == null) {
      return CoverageQuoteDTO.builder()
          .servicioId(servicio.getId())
          .servicioName(servicio.getName())
          .listPrice(listPrice)
          .serviceDiscount(discount)
          .promotionName(promotionName)
          .promotionReduction(promotionReduction)
          .netPrice(netPrice)
          .hasCoverage(false)
          .insurerCovers(Money.zero())
          .patientPays(netPrice)
          .build();
    }

    CoveragePlan plan = activeCoverage.getPlan();
    BigDecimal copay = plan.getCopayAmount() != null ? Money.of(plan.getCopayAmount()) : null;

    BigDecimal insurerCovers;
    BigDecimal patientPays;
    if (copay != null) {
      // Copay-based plan: coveragePercentage plays no part in the charge,
      // it is only echoed back for transparency/admin display. Clamp so a
      // misconfigured copay above the net price never overcharges.
      patientPays = copay.min(netPrice);
      insurerCovers = netPrice.subtract(patientPays);
    } else {
      // Percentage-based (coinsurance) plan.
      insurerCovers = Money.percentageOf(netPrice, BigDecimal.valueOf(plan.getCoveragePercentage()));
      patientPays = netPrice.subtract(insurerCovers);
    }

    return CoverageQuoteDTO.builder()
        .servicioId(servicio.getId())
        .servicioName(servicio.getName())
        .listPrice(listPrice)
        .serviceDiscount(discount)
        .promotionName(promotionName)
        .promotionReduction(promotionReduction)
        .netPrice(netPrice)
        .hasCoverage(true)
        .insurerName(plan.getInsurer() != null ? plan.getInsurer().getName() : null)
        .planName(plan.getName())
        .coveragePercentage(plan.getCoveragePercentage())
        .copayAmount(copay)
        .insurerCovers(insurerCovers)
        .patientPays(patientPays)
        .build();
  }
}
