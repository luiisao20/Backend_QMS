package com.devluis.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
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
public class HolidayDTO {
  private Long id;

  @NotNull(message = "La fecha del feriado es obligatoria")
  private LocalDate date;

  @NotBlank(message = "La descripción del feriado es requerida")
  private String description;

  // Absent/null = global (applies to every establishment). Present = applies
  // only to that establishment. Only `id` is read by the service.
  private StablishmentDTO stablishment;

  @NotNull(message = "El motivo del feriado es obligatorio")
  private BlockReasonDTO reason;

  private OffsetDateTime createdAt;

  // Populated only by create()/update(): ids of schedules that already had a
  // booked turn on this date and were therefore left untouched instead of
  // being auto-blocked. Null on plain reads (getAll/getById) — see apply
  // report for why this is not recomputed on every read.
  private List<Long> conflictingScheduleIds;
}
