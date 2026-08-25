package com.devluis.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for GET /api/metrics/patients.
 *
 * <p>cancellationRate = cancelledInPeriod / turnsInPeriod (0.0 when
 * turnsInPeriod is 0) — the fraction of booked turns in the period that
 * ended up cancelled, i.e. the slots that were wasted.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PatientsMetricsDTO {
  private LocalDate from;
  private LocalDate to;
  private long newPatients;
  private long turnsInPeriod;
  private long cancelledInPeriod;
  private double cancellationRate;
}
