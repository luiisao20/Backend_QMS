package com.devluis.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.devluis.types.TimeOffKind;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TimeOffDTO {
  private Long id;

  @NotNull(message = "El campo del doctor es obligatorio")
  private DoctorDTO doctor;

  @NotNull(message = "El campo del tipo es requerido")
  private TimeOffKind kind;

  @NotNull(message = "La fecha de inicio es obligatoria")
  private LocalDate startDate;

  @NotNull(message = "La fecha de fin es obligatoria")
  private LocalDate endDate;

  /** Opcional: el catalogo de motivos puede estar vacio todavia. */
  private BlockReasonDTO reason;

  private String notes;

  private OffsetDateTime createdAt;
}
