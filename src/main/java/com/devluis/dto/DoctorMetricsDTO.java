package com.devluis.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-doctor row for GET /api/metrics/employees.
 *
 * <p>noShows is a derived metric: TurnStatus has no explicit "no show" state,
 * so a no-show is defined as a turn still TURN_PENDING (never checked in)
 * whose schedule date has already passed. See MetricsService for the exact
 * query.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DoctorMetricsDTO {
  private UUID doctorId;
  private String firstName;
  private String lastName;
  private String speciality;
  private long attended;
  private long cancelled;
  private long noShows;
}
