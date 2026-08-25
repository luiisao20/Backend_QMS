package com.devluis.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal aggregation row: a plain count keyed by a UUID owner (e.g. the
 * doctor a no-show turn belongs to). Not part of the public API — only used
 * as a JPQL constructor-expression target (see TurnRepository) and reshaped
 * by MetricsService.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UuidCountRow {
  private UUID id;
  private Long total;
}
