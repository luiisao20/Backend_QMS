package com.devluis.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for GET /api/metrics/establishments. Includes every stablishment
 * (even ones with zero activity in the period) so a manager can spot an
 * under-used site, not just rank the busy ones.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EstablishmentsMetricsDTO {
  private LocalDate from;
  private LocalDate to;
  private List<EstablishmentMetricsDTO> establishments;
}
