package com.devluis.types;

// How a Promotion's discountValue is interpreted. Mirrors the
// coveragePercentage/copayAmount split already used by CoveragePlan: kept as
// two distinct, mutually exclusive modes on one entity instead of two
// separate Promotion subtypes.
public enum DiscountType {
  PERCENTAGE,
  FIXED_AMOUNT
}
