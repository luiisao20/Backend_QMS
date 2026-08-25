package com.devluis.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Response of "what would this patient pay for this service right now".
// insurerName/planName/coveragePercentage/copayAmount stay null when
// hasCoverage is false — see CoveragePricingService for the full rule.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CoverageQuoteDTO {
  private Long servicioId;
  private String servicioName;
  private BigDecimal listPrice;
  private BigDecimal serviceDiscount;
  // Both null when no Promotion is currently active for this service — same
  // "null means not applicable" idiom as insurerName/planName below.
  private String promotionName;
  private BigDecimal promotionReduction;
  private BigDecimal netPrice;
  private boolean hasCoverage;
  private String insurerName;
  private String planName;
  private Integer coveragePercentage;
  private BigDecimal copayAmount;
  private BigDecimal insurerCovers;
  private BigDecimal patientPays;
}
