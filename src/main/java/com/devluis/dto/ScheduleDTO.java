package com.devluis.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import com.devluis.types.ScheduleStatus;

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
public class ScheduleDTO {
  private Long id;

  @NotBlank(message = "El campo de la fecha es obligatorio")
  private LocalDate date;

  @NotBlank(message = "El campo de la hora es obligatorio")
  private LocalTime hour;

  private ScheduleStatus status;

  @NotNull(message = "El campo del doctor es obligatorio")
  private DoctorDTO doctor;

  @NotNull(message = "El campo del servicio es obligatorio")
  private ServicioDTO service;

  private OffsetDateTime createdAt;
}
