package com.devluis.dto;

import com.devluis.types.TurnStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal aggregation row: how many turns exist for a single (Long-keyed
 * owner, status) pair — used for the per-stablishment turn breakdown. Not
 * part of the public API — only used as a JPQL constructor-expression target
 * (see TurnRepository) and reshaped by MetricsService.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LongStatusCountRow {
  private Long id;
  private TurnStatus status;
  private Long total;
}
