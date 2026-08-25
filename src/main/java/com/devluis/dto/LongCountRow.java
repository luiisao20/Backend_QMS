package com.devluis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal aggregation row: a plain count keyed by a Long owner (a
 * stablishment id). Reused for total slots, occupied slots, doctors-per-
 * stablishment and services-per-stablishment — they are all "count of X for
 * stablishment Y" shaped queries. Not part of the public API — only used as a
 * JPQL constructor-expression target (see ScheduleRepository and
 * StablishmentRepository) and reshaped by MetricsService.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LongCountRow {
  private Long id;
  private Long total;
}
