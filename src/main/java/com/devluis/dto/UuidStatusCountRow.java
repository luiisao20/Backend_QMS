package com.devluis.dto;

import java.util.UUID;

import com.devluis.types.TurnStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal aggregation row: how many turns exist for a single (UUID-keyed
 * owner, status) pair — reused for both the per-doctor and per-operator
 * breakdowns, since Doctor and Operator share a UUID primary key type. Not
 * part of the public API — only used as a JPQL constructor-expression target
 * (see TurnRepository) and reshaped by MetricsService.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UuidStatusCountRow {
  private UUID id;
  private TurnStatus status;
  private Long total;
}
