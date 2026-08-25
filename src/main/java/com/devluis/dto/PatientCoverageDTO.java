package com.devluis.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

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
public class PatientCoverageDTO {
  private Long id;

  @NotNull(message = "El paciente es obligatorio")
  private PatientDTO patient;

  @NotNull(message = "El plan de cobertura es obligatorio")
  private CoveragePlanDTO plan;

  @NotBlank(message = "El número de póliza es obligatorio")
  private String policyNumber;

  @NotNull(message = "La fecha de inicio de vigencia es obligatoria")
  private LocalDate validFrom;

  // Nullable: an ongoing policy with no known end date yet.
  private LocalDate validUntil;

  @NotNull(message = "Debe indicar si esta cobertura está activa")
  private Boolean active;

  private OffsetDateTime createdAt;
}
