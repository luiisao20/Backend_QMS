package com.devluis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-establishment row for GET /api/metrics/establishments.
 *
 * <p>occupancyRate = occupiedSlots / totalSlots (0.0 when totalSlots is 0).
 * occupiedSlots counts DISTINCT schedules that have at least one non-
 * cancelled turn in the period; totalSlots counts every schedule offered in
 * the period, regardless of status. See MetricsService for why
 * Schedule.status is deliberately NOT used to compute this (it is never
 * transitioned to STATUS_OCCUPIED by the booking flow).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EstablishmentMetricsDTO {
  private Long stablishmentId;
  private String name;
  private long servicesCount;
  private long doctorsCount;
  private TurnStatusBreakdownDTO turns;
  private long totalSlots;
  private long occupiedSlots;
  private double occupancyRate;
}
