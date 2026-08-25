package com.devluis.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.devluis.types.TimeOffKind;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeOffDTO {
  private Long id;

  @NotNull(message = "El doctor es obligatorio")
  private DoctorDTO doctor;

  @NotNull(message = "El tipo de ausencia (vacaciones o permiso) es obligatorio")
  private TimeOffKind kind;

  @NotNull(message = "La fecha de inicio es obligatoria")
  private LocalDate startDate;

  @NotNull(message = "La fecha de fin es obligatoria")
  private LocalDate endDate;

  @NotNull(message = "El motivo de la ausencia es obligatorio")
  private BlockReasonDTO reason;

  private OffsetDateTime createdAt;

  // Populated only by create()/update() — see HolidayDTO#conflictingScheduleIds.
  private List<Long> conflictingScheduleIds;
}
