package com.devluis.types;

// What kind of clinical read ClinicalAccessLog is recording. The *_LIST
// variants back a single log entry for a whole "browse this patient's
// history/prescriptions" call (one entry per HTTP call, not one per row
// returned) — see ClinicalAccessLogService for why per-row logging was
// rejected as impractical noise.
public enum ClinicalResourceType {
  ENCOUNTER,
  ENCOUNTER_LIST,
  PRESCRIPTION,
  PRESCRIPTION_LIST
}
