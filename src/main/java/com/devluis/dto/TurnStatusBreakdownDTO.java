package com.devluis.dto;

import java.util.Map;

import com.devluis.types.TurnStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reusable shape for "how many turns, split by status" — every TurnStatus is
 * always present as a key (zero-filled by MetricsService when a status has
 * no turns), so clients can render a chart/table without checking for
 * missing keys. Shared by MetricsSummaryDTO (today), DayTurnsDTO (per day)
 * and EstablishmentMetricsDTO (per stablishment) for one consistent contract
 * across the whole metrics API.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TurnStatusBreakdownDTO {
  private Map<TurnStatus, Long> byStatus;
  private long total;
}
