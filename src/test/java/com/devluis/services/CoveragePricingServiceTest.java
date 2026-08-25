package com.devluis.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.devluis.dto.CoverageQuoteDTO;
import com.devluis.entity.CoveragePlan;
import com.devluis.entity.Insurer;
import com.devluis.entity.PatientCoverage;
import com.devluis.entity.Promotion;
import com.devluis.entity.Servicio;
import com.devluis.types.DiscountType;
import com.devluis.types.InsurerType;

/**
 * Pure calculator, no repositories involved.
 *
 * <p>Model: {@code copayAmount} and {@code coveragePercentage} are treated as
 * MUTUALLY EXCLUSIVE levers on the same plan, copay taking precedence when
 * both are set. An earlier "copay charged first, then coinsurance on the
 * remainder" design was rejected during TDD (red phase) precisely because it
 * has a degenerate case: at {@code coveragePercentage = 0} the copay becomes
 * a complete no-op (the patient still pays the full net price), which
 * contradicts what a "copay plan" is supposed to mean. An additive model
 * (coinsurance share PLUS the copay on top) was rejected earlier for the
 * opposite reason: it can charge the patient MORE than the service costs.
 * Mutually-exclusive levers avoid both traps at the cost of not being able to
 * express "a copay AND additional coinsurance beyond it" — see
 * CoveragePlan's docblock and the apply report.
 */
class CoveragePricingServiceTest {

  private final CoveragePricingService pricingService = new CoveragePricingService();

  private Servicio servicio(float price, Float discount) {
    return Servicio.builder().id(1L).name("Consulta general").price(price).discount(discount).build();
  }

  private Insurer insurer() {
    return Insurer.builder().id(1L).name("Seguros Sucre").type(InsurerType.INSURER_PRIVATE).build();
  }

  private PatientCoverage coverageWith(int coveragePercentage, BigDecimal copayAmount) {
    CoveragePlan plan = CoveragePlan.builder()
        .id(1L)
        .insurer(insurer())
        .name("Plan Oro")
        .coveragePercentage(coveragePercentage)
        .copayAmount(copayAmount)
        .build();
    return PatientCoverage.builder().id(1L).plan(plan).active(true).build();
  }

  @Test
  void quote_withNoCoverage_patientPaysTheFullNetPrice() {
    CoverageQuoteDTO quote = pricingService.quote(servicio(100f, null), null);

    assertThat(quote.isHasCoverage()).isFalse();
    assertThat(quote.getNetPrice()).isEqualByComparingTo("100.00");
    assertThat(quote.getPatientPays()).isEqualByComparingTo("100.00");
    assertThat(quote.getInsurerCovers()).isEqualByComparingTo("0.00");
    assertThat(quote.getInsurerName()).isNull();
    assertThat(quote.getPlanName()).isNull();
  }

  @Test
  void quote_appliesServiceDiscount_beforeCoverage() {
    CoverageQuoteDTO quote = pricingService.quote(servicio(100f, 10f), null);

    assertThat(quote.getListPrice()).isEqualByComparingTo("100.00");
    assertThat(quote.getServiceDiscount()).isEqualByComparingTo("10.00");
    assertThat(quote.getNetPrice()).isEqualByComparingTo("90.00");
    assertThat(quote.getPatientPays()).isEqualByComparingTo("90.00");
  }

  @Test
  void quote_discountLargerThanPrice_clampsNetPriceAtZero_insteadOfGoingNegative() {
    CoverageQuoteDTO quote = pricingService.quote(servicio(20f, 50f), null);

    assertThat(quote.getNetPrice()).isEqualByComparingTo("0.00");
    assertThat(quote.getPatientPays()).isEqualByComparingTo("0.00");
  }

  @Test
  void quote_percentageOnly_splitsNetPriceBetweenInsurerAndPatient() {
    PatientCoverage coverage = coverageWith(80, null);

    CoverageQuoteDTO quote = pricingService.quote(servicio(100f, null), coverage);

    assertThat(quote.isHasCoverage()).isTrue();
    assertThat(quote.getInsurerName()).isEqualTo("Seguros Sucre");
    assertThat(quote.getPlanName()).isEqualTo("Plan Oro");
    assertThat(quote.getCoveragePercentage()).isEqualTo(80);
    assertThat(quote.getInsurerCovers()).isEqualByComparingTo("80.00");
    assertThat(quote.getPatientPays()).isEqualByComparingTo("20.00");
  }

  @Test
  void quote_fullCoveragePercentage_patientPaysNothing() {
    PatientCoverage coverage = coverageWith(100, null);

    CoverageQuoteDTO quote = pricingService.quote(servicio(100f, null), coverage);

    assertThat(quote.getInsurerCovers()).isEqualByComparingTo("100.00");
    assertThat(quote.getPatientPays()).isEqualByComparingTo("0.00");
  }

  @Test
  void quote_zeroPercentage_noCopay_patientPaysTheFullNetPrice() {
    PatientCoverage coverage = coverageWith(0, null);

    CoverageQuoteDTO quote = pricingService.quote(servicio(100f, null), coverage);

    assertThat(quote.getInsurerCovers()).isEqualByComparingTo("0.00");
    assertThat(quote.getPatientPays()).isEqualByComparingTo("100.00");
  }

  @Test
  void quote_copaySet_patientPaysExactlyTheCopay_insurerCoversTheRest_percentageIgnored() {
    // coveragePercentage is deliberately set to something that would produce
    // a DIFFERENT result if it were also applied, to prove copay takes
    // precedence rather than combining.
    PatientCoverage coverage = coverageWith(50, new BigDecimal("10.00"));

    CoverageQuoteDTO quote = pricingService.quote(servicio(100f, null), coverage);

    assertThat(quote.getCopayAmount()).isEqualByComparingTo("10.00");
    assertThat(quote.getPatientPays()).isEqualByComparingTo("10.00");
    assertThat(quote.getInsurerCovers()).isEqualByComparingTo("90.00");
    // coveragePercentage is still reported for transparency/admin display,
    // even though it played no part in the charge.
    assertThat(quote.getCoveragePercentage()).isEqualTo(50);
  }

  @Test
  void quote_copayAboveNetPrice_isClampedSoThePatientNeverPaysMoreThanTheService() {
    PatientCoverage coverage = coverageWith(50, new BigDecimal("999.00"));

    CoverageQuoteDTO quote = pricingService.quote(servicio(30f, null), coverage);

    assertThat(quote.getPatientPays()).isEqualByComparingTo("30.00");
    assertThat(quote.getInsurerCovers()).isEqualByComparingTo("0.00");
  }

  @Test
  void quote_rounding_usesHalfUpOnTheInsurerShare_andNeverLosesOrCreatesMoney() {
    // price 10, 33% coverage -> insurer share is 3.3 (no rounding ambiguity
    // here); the important assertion is the invariant below, which holds
    // regardless of the exact split.
    PatientCoverage coverage = coverageWith(33, null);

    CoverageQuoteDTO quote = pricingService.quote(servicio(10f, null), coverage);

    assertThat(quote.getInsurerCovers()).isEqualByComparingTo("3.30");
    assertThat(quote.getPatientPays()).isEqualByComparingTo("6.70");
    assertThat(quote.getInsurerCovers().add(quote.getPatientPays())).isEqualByComparingTo(quote.getNetPrice());
  }

  @Test
  void quote_rounding_halfUpOnAnExactMidpointCent() {
    // price 0.05, 50% coverage -> exact half-cent (0.025) must round UP to
    // 0.03, not down to 0.02.
    PatientCoverage coverage = coverageWith(50, null);

    CoverageQuoteDTO quote = pricingService.quote(servicio(0.05f, null), coverage);

    assertThat(quote.getInsurerCovers()).isEqualByComparingTo("0.03");
    assertThat(quote.getPatientPays()).isEqualByComparingTo("0.02");
  }

  // --- Promotion integration -------------------------------------------
  // A currently-active Promotion is applied AFTER Servicio.discount and
  // BEFORE insurance evaluates its share — see the class docblock for why
  // this ordering (not insurance-first) was chosen.

  private Promotion percentagePromotion(String percentage) {
    return Promotion.builder().id(1L).name("Promo Verano")
        .discountType(DiscountType.PERCENTAGE).discountValue(new BigDecimal(percentage))
        .startDate(java.time.LocalDate.now().minusDays(1)).endDate(java.time.LocalDate.now().plusDays(1)).build();
  }

  private Promotion fixedAmountPromotion(String amount) {
    return Promotion.builder().id(2L).name("Promo Fija")
        .discountType(DiscountType.FIXED_AMOUNT).discountValue(new BigDecimal(amount))
        .startDate(java.time.LocalDate.now().minusDays(1)).endDate(java.time.LocalDate.now().plusDays(1)).build();
  }

  @Test
  void quote_withNoPromotion_behavesExactlyAsTheTwoArgOverload() {
    CoverageQuoteDTO quote = pricingService.quote(servicio(100f, 10f), null, null);

    assertThat(quote.getNetPrice()).isEqualByComparingTo("90.00");
    assertThat(quote.getPromotionName()).isNull();
    assertThat(quote.getPromotionReduction()).isNull();
  }

  @Test
  void quote_percentagePromotion_appliesOnTopOfServiceDiscount_beforeInsurance() {
    // price 100, service discount 10 -> preNetPrice 90; promotion 20% of 90 = 18.
    CoverageQuoteDTO quote = pricingService.quote(servicio(100f, 10f), null, percentagePromotion("20"));

    assertThat(quote.getPromotionName()).isEqualTo("Promo Verano");
    assertThat(quote.getPromotionReduction()).isEqualByComparingTo("18.00");
    assertThat(quote.getNetPrice()).isEqualByComparingTo("72.00");
    assertThat(quote.getPatientPays()).isEqualByComparingTo("72.00");
  }

  @Test
  void quote_fixedAmountPromotion_subtractsFlatAmount() {
    CoverageQuoteDTO quote = pricingService.quote(servicio(100f, null), null, fixedAmountPromotion("15.00"));

    assertThat(quote.getPromotionReduction()).isEqualByComparingTo("15.00");
    assertThat(quote.getNetPrice()).isEqualByComparingTo("85.00");
  }

  @Test
  void quote_fixedAmountPromotionLargerThanNetPrice_isClampedInsteadOfGoingNegative() {
    CoverageQuoteDTO quote = pricingService.quote(servicio(10f, null), null, fixedAmountPromotion("999.00"));

    assertThat(quote.getNetPrice()).isEqualByComparingTo("0.00");
    assertThat(quote.getPatientPays()).isEqualByComparingTo("0.00");
  }

  @Test
  void quote_promotionAndInsurance_bothApply_insuranceSplitsThePromotedNetPrice() {
    // price 100, no service discount -> preNetPrice 100; promotion 50% -> netPrice 50;
    // insurance covers 80% of that 50 = 40; patient pays 10.
    PatientCoverage coverage = coverageWith(80, null);

    CoverageQuoteDTO quote = pricingService.quote(servicio(100f, null), coverage, percentagePromotion("50"));

    assertThat(quote.getNetPrice()).isEqualByComparingTo("50.00");
    assertThat(quote.getInsurerCovers()).isEqualByComparingTo("40.00");
    assertThat(quote.getPatientPays()).isEqualByComparingTo("10.00");
    assertThat(quote.getInsurerCovers().add(quote.getPatientPays())).isEqualByComparingTo(quote.getNetPrice());
  }
}
