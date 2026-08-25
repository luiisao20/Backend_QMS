package com.devluis.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.devluis.types.ClinicalResourceType;
import com.devluis.types.Role;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// The "who read this patient's history" ledger for reportes/auditoria-hc.
// Deliberately NOT modelled with JPA relations to Patient/Doctor/Operator:
// this is an append-only fact ledger, not a graph to navigate, and a raw
// UUID column can never be silently emptied by a cascade against the
// account it references (the same class of mistake the Schedule/Turn
// "never cascade" rule exists to prevent, applied here to protect the audit
// trail itself instead of a booking). See apply report for the tradeoff
// (no denormalized display name captured at access time — a future
// enhancement, not built here).
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "clinical_access_logs")
@Entity
public class ClinicalAccessLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private UUID patientUuid;

  @Column(nullable = false)
  private UUID accessedByUuid;

  // Nullable on purpose, unlike accessedByUuid/resourceType: every real token
  // in this system carries exactly one recognized Role authority today (see
  // ClinicalAccessLogService#resolveRole), so this is realistically always
  // populated — but audit-logging must never be the reason a legitimate,
  // already-authorized clinical read fails. If role resolution ever came up
  // empty, the write still succeeds (degraded row) instead of throwing a
  // constraint violation that would abort the read it is trying to record.
  @Enumerated(EnumType.STRING)
  private Role accessedByRole;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ClinicalResourceType resourceType;

  // Null for *_LIST resource types (one log entry per "browse this patient's
  // history" call, not one per row returned — see ClinicalAccessLogService).
  private Long resourceId;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime accessedAt;
}
