package com.devluis.dto;

import java.time.LocalDate;

import com.devluis.types.TurnStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal aggregation row: how many turns exist for a single (day, status)
 * pair. Not part of the public API — only used as a JPQL constructor-expression
 * target (see TurnRepository) and reshaped into the /turns time series by
 * MetricsService.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DateStatusCountRow {
  private LocalDate date;
  private TurnStatus status;
  private Long total;
}
