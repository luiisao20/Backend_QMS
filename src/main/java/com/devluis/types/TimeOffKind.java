package com.devluis.types;

// Discriminator for TimeOff: one table backs the two "bloqueo de citas" admin
// destinations /vacaciones and /permisos. KIND_VACATION and KIND_PERMISSION
// are functionally identical (a doctor unavailable over a date range) — the
// only difference is administrative classification, so a single enum field
// is enough instead of two separate entities/tables.
public enum TimeOffKind {
  KIND_VACATION,
  KIND_PERMISSION
}
