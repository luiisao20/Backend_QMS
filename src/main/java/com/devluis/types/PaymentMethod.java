package com.devluis.types;

// How a Payment was received. INSURER_SETTLEMENT doubles as the "who paid"
// signal for accounting purposes (see AccountingService) — there is
// deliberately no separate payerType field: a Payment received through any
// of the other three methods is, by construction in this codebase, always
// money handed over by the patient (or on the patient's behalf) at the
// front desk, while INSURER_SETTLEMENT is only ever created by
// ClaimService#markAsPaid. Adding a second, independent "payer" field would
// let the two disagree with each other for no gain.
public enum PaymentMethod {
  CASH,
  CARD,
  TRANSFER,
  INSURER_SETTLEMENT
}
