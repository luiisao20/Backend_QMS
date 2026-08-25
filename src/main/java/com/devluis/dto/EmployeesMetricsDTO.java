package com.devluis.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for GET /api/metrics/employees. Includes every doctor and
 * operator (even with zero activity in the period), same reasoning as
 * EstablishmentsMetricsDTO.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EmployeesMetricsDTO {
  private LocalDate from;
  private LocalDate to;
  private List<DoctorMetricsDTO> doctors;
  private List<OperatorMetricsDTO> operators;
}
