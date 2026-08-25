package com.devluis.types;

// Lifecycle of an Invoice. VOID is the ONLY "removal" mechanism — see
// Invoice's own docblock for why there is no hard delete, same reasoning as
// Encounter/Prescription in the clinical group.
//
// ISSUED / PARTIALLY_PAID / PAID are NEVER set directly by a caller — they
// are recomputed by InvoiceService every time a Payment is registered, purely
// as a function of (total, sum(payments)). VOID is the only status reachable
// through an explicit, guarded transition (InvoiceService#voidInvoice).
public enum InvoiceStatus {
  ISSUED,
  PARTIALLY_PAID,
  PAID,
  VOID
}
