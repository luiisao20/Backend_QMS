package com.devluis.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Small catalog of reasons a Schedule slot was blocked, referenced by both
// Holiday and TimeOff. Kept intentionally minimal (no "active" flag): a
// BlockReason that is no longer wanted is either renamed (update) or removed
// (delete), and delete is guarded by BlockReasonService against any Holiday
// or TimeOff still referencing it — see that guard for why a soft-disable
// flag was not added on top.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "block_reasons")
@Entity
public class BlockReason {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String description;

  @CreationTimestamp
  @Column(columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
