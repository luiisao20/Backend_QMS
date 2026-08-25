package com.devluis.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One point in the /api/metrics/turns time series: a calendar day and its
 * turn breakdown by status. Every day in the requested range is present
 * (zero-filled), even days with no turns at all, so a chart's x-axis stays
 * continuous.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DayTurnsDTO {
  private LocalDate date;
  private TurnStatusBreakdownDTO turns;
}
