package com.devluis.types;

// Lifecycle of a Claim (money billed to an INSURER, not the patient).
//
// SUBMITTED -> ACCEPTED -> PAID is the happy path (ClaimService#markAsPaid
// creates the settlement Payment and is only reachable from ACCEPTED).
// SUBMITTED -> REJECTED is terminal for THIS claim (no re-opening a rejected
// claim — see ClaimService docblock for why a brand-new claim, not a
// resubmission workflow, is the deliberately minimal answer). A rejection
// does NOT change the Invoice's total or balance — see ClaimService and the
// apply report for why the existing (total - payments) balance formula
// already answers "where does the money come from now".
public enum ClaimStatus {
  SUBMITTED,
  ACCEPTED,
  REJECTED,
  PAID
}
