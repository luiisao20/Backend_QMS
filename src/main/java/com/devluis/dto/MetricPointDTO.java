package com.devluis.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Un punto de una serie de tiempo: una fecha y su total. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MetricPointDTO {
  private LocalDate date;
  private Long total;
}
