package com.devluis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for GET /api/metrics/summary — the admin dashboard header. One
 * request instead of six: turns today by status, plus current totals for
 * every catalog resource. Totals are absolute counts (not period-scoped) —
 * "how many patients/doctors/operators/establishments/services exist right
 * now".
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MetricsSummaryDTO {
  private TurnStatusBreakdownDTO turnsToday;
  private long totalPatients;
  private long totalDoctors;
  private long totalOperators;
  private long totalEstablishments;
  private long totalServices;
}
