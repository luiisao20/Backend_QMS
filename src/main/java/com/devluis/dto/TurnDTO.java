package com.devluis.dto;

import java.time.OffsetDateTime;

import com.devluis.types.TurnStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TurnDTO {
  private Long id;

  private Integer order;

  private TurnStatus status;

  private OffsetDateTime createdAt;

  private OffsetDateTime finishedAt;

  private OffsetDateTime cancelledAt;

  private OperatorDTO operator;

  private PatientDTO patient;

  private ScheduleDTO schedule;

}
