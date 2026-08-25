package com.devluis.types;

// What ONE InvoiceLineItem was issued FOR. `sourceId` on the line item is a
// plain, unenforced-FK Long (see InvoiceLineItem's docblock) — this enum is
// what tells a reader HOW to interpret that id (a Turn id, a ServicePackage
// id, a SessionPlan id, or nothing at all for FREE_LINE).
//
// Only TURN lines can ever carry a non-zero insurerCoveredAmount: they are
// the only source priced through CoveragePricingService. PACKAGE and
// SESSION_PLAN carry their own flat price and deliberately do NOT flow
// through coverage pricing (see ServicePackage/SessionPlan docblocks) — a
// package/session-plan line is always 100% patient-responsible. FREE_LINE is
// a manually-entered charge (e.g. a late fee, a document copy) with no
// catalog backing at all.
public enum InvoiceLineSourceType {
  TURN,
  PACKAGE,
  SESSION_PLAN,
  FREE_LINE
}
