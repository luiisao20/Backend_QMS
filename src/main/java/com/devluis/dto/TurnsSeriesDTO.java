package com.devluis.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for GET /api/metrics/turns — feeds dashboard/analytics and
 * reportes/general. Echoes back the resolved from/to (which may have been
 * defaulted server-side) and the optional filters actually applied, so the
 * client never has to guess what range it got.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TurnsSeriesDTO {
  private LocalDate from;
  private LocalDate to;
  private Long stablishmentId;
  private Long serviceId;
  private List<DayTurnsDTO> days;
}
