package com.devluis.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
public class PrescriptionDTO {
  private Long id;

  @NotNull(message = "La historia clínica (encounterId) es obligatoria")
  private Long encounterId;

  private String notes;

  @NotEmpty(message = "La receta debe tener al menos un medicamento")
  @Valid
  private List<PrescriptionItemDTO> items;

  private OffsetDateTime createdAt;

  // Read-only, derived from encounter.turn.schedule.doctor.
  private UUID doctorUuid;
  private String doctorFullName;
}
