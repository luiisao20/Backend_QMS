package com.devluis.dto;

import com.devluis.types.TurnStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal aggregation row: how many turns exist for a single status.
 * Not part of the public API — only used as a JPQL constructor-expression
 * target (see TurnRepository) and reshaped into TurnStatusBreakdownDTO by
 * MetricsService.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StatusCountRow {
  private TurnStatus status;
  private Long total;
}
